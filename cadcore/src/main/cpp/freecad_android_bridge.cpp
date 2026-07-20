#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdlib>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

#ifdef FREECAD_OCC_AVAILABLE
#include <BRepBuilderAPI_Transform.hxx>
#include <BRepMesh_IncrementalMesh.hxx>
#include <BRep_Tool.hxx>
#include <IFSelect_ReturnStatus.hxx>
#include <Poly_Triangle.hxx>
#include <Poly_Triangulation.hxx>
#include <STEPControl_Reader.hxx>
#include <TopAbs_Orientation.hxx>
#include <TopExp_Explorer.hxx>
#include <TopLoc_Location.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Shape.hxx>
#include <gp_Pnt.hxx>
#include <gp_Trsf.hxx>
#endif

namespace {
thread_local std::string last_error;
constexpr jlong CAP_STEP_IMPORT = 1LL << 0;
constexpr jlong CAP_TESSELLATION = 1LL << 1;
constexpr jlong CAP_BOUNDING_BOX = 1LL << 2;
constexpr jlong CAP_RIGID_TRANSFORM = 1LL << 3;

jstring text(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string utf8(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jfloatArray floats(JNIEnv* env, const std::vector<float>& values) {
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(values.size()));
    if (result != nullptr && !values.empty()) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    }
    return result;
}

jintArray integers(JNIEnv* env, const std::vector<jint>& values) {
    jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
    if (result != nullptr && !values.empty()) {
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    }
    return result;
}

jdoubleArray doubles(JNIEnv* env, const std::vector<double>& values) {
    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(values.size()));
    if (result != nullptr && !values.empty()) {
        env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    }
    return result;
}

bool rigid(const double* m) {
    if (m == nullptr) return false;
    const double nx=m[0]*m[0]+m[4]*m[4]+m[8]*m[8];
    const double ny=m[1]*m[1]+m[5]*m[5]+m[9]*m[9];
    const double nz=m[2]*m[2]+m[6]*m[6]+m[10]*m[10];
    const double xy=m[0]*m[1]+m[4]*m[5]+m[8]*m[9];
    const double xz=m[0]*m[2]+m[4]*m[6]+m[8]*m[10];
    const double yz=m[1]*m[2]+m[5]*m[6]+m[9]*m[10];
    const double eps=1e-5;
    return std::abs(nx-1)<=eps&&std::abs(ny-1)<=eps&&std::abs(nz-1)<=eps
            &&std::abs(xy)<=eps&&std::abs(xz)<=eps&&std::abs(yz)<=eps
            &&std::abs(m[12])<=eps&&std::abs(m[13])<=eps
            &&std::abs(m[14])<=eps&&std::abs(m[15]-1)<=eps;
}

#ifdef FREECAD_OCC_AVAILABLE
struct ShapeRecord {
    TopoDS_Shape shape;
    std::vector<float> vertices;
    std::vector<float> normals;
    std::vector<jint> triangles;
    std::vector<double> bounds;
    double linear_deflection = 0.2;
    double angular_deflection_degrees = 15.0;
};

std::atomic<jlong> next_handle{1};
std::mutex records_mutex;
std::unordered_map<jlong, std::shared_ptr<ShapeRecord>> records;

void add_normal(std::vector<float>& normals, jint index, float nx, float ny, float nz) {
    const std::size_t base=static_cast<std::size_t>(index)*3;
    normals[base]+=nx; normals[base+1]+=ny; normals[base+2]+=nz;
}

std::shared_ptr<ShapeRecord> tessellate(const TopoDS_Shape& shape,
                                        double linear_deflection,
                                        double angular_degrees) {
    if (shape.IsNull()) throw std::runtime_error("OCCT shape is null");
    auto record=std::make_shared<ShapeRecord>();
    record->shape=shape;
    record->linear_deflection=std::max(1e-5,linear_deflection);
    record->angular_deflection_degrees=std::max(0.1,std::min(90.0,angular_degrees));
    const double angular_radians=record->angular_deflection_degrees*3.14159265358979323846/180.0;
    BRepMesh_IncrementalMesh mesher(shape,record->linear_deflection,false,angular_radians,true);
    if (!mesher.IsDone()) throw std::runtime_error("BRepMesh_IncrementalMesh did not finish");

    double min_x=0,min_y=0,min_z=0,max_x=0,max_y=0,max_z=0;
    bool have_bounds=false;
    for (TopExp_Explorer explorer(shape,TopAbs_FACE); explorer.More(); explorer.Next()) {
        const TopoDS_Shape& face_shape=explorer.Current();
        TopLoc_Location location;
        occ::handle<Poly_Triangulation> triangulation=
                BRep_Tool::Triangulation(TopoDS::Face(face_shape),location);
        if (triangulation.IsNull()||triangulation->NbTriangles()==0) continue;
        const gp_Trsf transform=location.Transformation();
        const jint offset=static_cast<jint>(record->vertices.size()/3);
        record->vertices.reserve(record->vertices.size()+static_cast<std::size_t>(triangulation->NbNodes())*3);
        record->normals.resize(record->normals.size()+static_cast<std::size_t>(triangulation->NbNodes())*3,0.0f);
        for (int node=1;node<=triangulation->NbNodes();++node) {
            gp_Pnt point=triangulation->Node(node);
            point.Transform(transform);
            const float x=static_cast<float>(point.X());
            const float y=static_cast<float>(point.Y());
            const float z=static_cast<float>(point.Z());
            record->vertices.push_back(x);record->vertices.push_back(y);record->vertices.push_back(z);
            if(!have_bounds){min_x=max_x=x;min_y=max_y=y;min_z=max_z=z;have_bounds=true;}
            else{min_x=std::min(min_x,static_cast<double>(x));max_x=std::max(max_x,static_cast<double>(x));
                min_y=std::min(min_y,static_cast<double>(y));max_y=std::max(max_y,static_cast<double>(y));
                min_z=std::min(min_z,static_cast<double>(z));max_z=std::max(max_z,static_cast<double>(z));}
        }
        const TopAbs_Orientation orientation=face_shape.Orientation();
        for (int triangle_index=1;triangle_index<=triangulation->NbTriangles();++triangle_index) {
            Poly_Triangle triangle=triangulation->Triangle(triangle_index);
            int ids[3];triangle.Get(ids[0],ids[1],ids[2]);
            if(orientation==TopAbs_REVERSED)std::swap(ids[1],ids[2]);
            const jint a=offset+ids[0]-1,b=offset+ids[1]-1,c=offset+ids[2]-1;
            record->triangles.push_back(a);record->triangles.push_back(b);record->triangles.push_back(c);
            const std::size_t ia=static_cast<std::size_t>(a)*3,ib=static_cast<std::size_t>(b)*3,ic=static_cast<std::size_t>(c)*3;
            const float ux=record->vertices[ib]-record->vertices[ia];
            const float uy=record->vertices[ib+1]-record->vertices[ia+1];
            const float uz=record->vertices[ib+2]-record->vertices[ia+2];
            const float vx=record->vertices[ic]-record->vertices[ia];
            const float vy=record->vertices[ic+1]-record->vertices[ia+1];
            const float vz=record->vertices[ic+2]-record->vertices[ia+2];
            const float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            add_normal(record->normals,a,nx,ny,nz);add_normal(record->normals,b,nx,ny,nz);add_normal(record->normals,c,nx,ny,nz);
        }
    }
    if(record->triangles.empty())throw std::runtime_error("OCCT shape produced no triangles");
    for(std::size_t i=0;i<record->normals.size();i+=3){
        const double length=std::sqrt(record->normals[i]*record->normals[i]
                +record->normals[i+1]*record->normals[i+1]
                +record->normals[i+2]*record->normals[i+2]);
        if(length>1e-12){record->normals[i]=static_cast<float>(record->normals[i]/length);
            record->normals[i+1]=static_cast<float>(record->normals[i+1]/length);
            record->normals[i+2]=static_cast<float>(record->normals[i+2]/length);}
    }
    record->bounds={min_x,min_y,min_z,max_x,max_y,max_z};
    return record;
}

jlong store_record(const std::shared_ptr<ShapeRecord>& record){
    const jlong handle=next_handle.fetch_add(1);
    std::lock_guard<std::mutex> lock(records_mutex);records[handle]=record;return handle;
}

std::shared_ptr<ShapeRecord> record_for(jlong handle){
    std::lock_guard<std::mutex> lock(records_mutex);auto found=records.find(handle);
    return found==records.end()?nullptr:found->second;
}

void configure_resources(const std::string& root){
    if(root.empty())throw std::runtime_error("OCCT resource root is empty");
    auto set_path=[&root](const char* name,const char* child){
        const std::string value=root+"/"+child;
        if(setenv(name,value.c_str(),1)!=0)throw std::runtime_error(std::string("setenv failed for ")+name);
    };
    setenv("CSF_LANGUAGE","us",1);setenv("MMGT_CLEAR","1",1);
    set_path("CSF_SHMessage","SHMessage");set_path("CSF_XSMessage","XSMessage");
    set_path("CSF_StandardDefaults","StdResource");set_path("CSF_PluginDefaults","StdResource");
    set_path("CSF_XCAFDefaults","StdResource");set_path("CSF_STEPDefaults","XSTEPResource");
    set_path("CSF_IGESDefaults","XSTEPResource");
}
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_runtimeInfo(JNIEnv* env,jclass){
#ifdef FREECAD_OCC_AVAILABLE
    return text(env,"OCCT 8.0.0 Android STEP bridge 2.0");
#else
    return text(env,"FreeCAD Android JNI bridge 2.0; OCCT kernel not linked");
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_capabilitiesMask(JNIEnv*,jclass){
#ifdef FREECAD_OCC_AVAILABLE
    return CAP_STEP_IMPORT|CAP_TESSELLATION|CAP_BOUNDING_BOX|CAP_RIGID_TRANSFORM;
#else
    return 0;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_lastError(JNIEnv* env,jclass){return text(env,last_error);}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_initializeResources(JNIEnv* env,jclass,jstring root){
#ifdef FREECAD_OCC_AVAILABLE
    try{configure_resources(utf8(env,root));last_error.clear();return JNI_TRUE;}
    catch(const std::exception& error){last_error=error.what();return JNI_FALSE;}
#else
    (void)env;(void)root;last_error="OCCT resource initialization requires linked kernel";return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_importStep(
        JNIEnv* env,jclass,jstring path,jdouble linear_deflection,jdouble angular_deflection){
#ifdef FREECAD_OCC_AVAILABLE
    try{
        const std::string file=utf8(env,path);if(file.empty())throw std::runtime_error("STEP path is empty");
        STEPControl_Reader reader;const IFSelect_ReturnStatus read_status=reader.ReadFile(file.c_str());
        if(read_status!=IFSelect_RetDone)throw std::runtime_error("STEPControl_Reader failed with status "+std::to_string(static_cast<int>(read_status)));
        const int transferred=reader.TransferRoots();if(transferred<=0)throw std::runtime_error("STEP file contains no transferable roots");
        TopoDS_Shape shape=reader.OneShape();auto record=tessellate(shape,linear_deflection,angular_deflection);
        last_error.clear();return store_record(record);
    }catch(const std::exception& error){last_error=error.what();return 0;}
#else
    (void)env;(void)path;(void)linear_deflection;(void)angular_deflection;
    last_error="OCCT/FreeCAD STEP kernel is not linked in this build";return 0;
#endif
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_meshVertices(JNIEnv* env,jclass,jlong handle){
#ifdef FREECAD_OCC_AVAILABLE
    auto record=record_for(handle);if(record==nullptr){last_error="Unknown native shape handle";return env->NewFloatArray(0);}return floats(env,record->vertices);
#else
    (void)handle;return env->NewFloatArray(0);
#endif
}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_meshNormals(JNIEnv* env,jclass,jlong handle){
#ifdef FREECAD_OCC_AVAILABLE
    auto record=record_for(handle);if(record==nullptr){last_error="Unknown native shape handle";return env->NewFloatArray(0);}return floats(env,record->normals);
#else
    (void)handle;return env->NewFloatArray(0);
#endif
}
extern "C" JNIEXPORT jintArray JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_meshTriangles(JNIEnv* env,jclass,jlong handle){
#ifdef FREECAD_OCC_AVAILABLE
    auto record=record_for(handle);if(record==nullptr){last_error="Unknown native shape handle";return env->NewIntArray(0);}return integers(env,record->triangles);
#else
    (void)handle;return env->NewIntArray(0);
#endif
}
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_boundingBox(JNIEnv* env,jclass,jlong handle){
#ifdef FREECAD_OCC_AVAILABLE
    auto record=record_for(handle);if(record==nullptr){last_error="Unknown native shape handle";return env->NewDoubleArray(0);}return doubles(env,record->bounds);
#else
    (void)handle;return env->NewDoubleArray(0);
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_transformedCopy(
        JNIEnv* env,jclass,jlong handle,jdoubleArray matrix){
#ifdef FREECAD_OCC_AVAILABLE
    try{
        auto source=record_for(handle);if(source==nullptr)throw std::runtime_error("Unknown native shape handle");
        if(matrix==nullptr||env->GetArrayLength(matrix)!=16)throw std::runtime_error("Rigid transform must contain 16 values");
        double values[16];env->GetDoubleArrayRegion(matrix,0,16,values);
        if(!rigid(values))throw std::runtime_error("Non-rigid transform rejected");
        gp_Trsf transform;transform.SetValues(values[0],values[1],values[2],values[3],
                values[4],values[5],values[6],values[7],values[8],values[9],values[10],values[11]);
        BRepBuilderAPI_Transform operation(source->shape,transform,true);
        if(!operation.IsDone())throw std::runtime_error("BRepBuilderAPI_Transform failed");
        auto result=tessellate(operation.Shape(),source->linear_deflection,source->angular_deflection_degrees);
        last_error.clear();return store_record(result);
    }catch(const std::exception& error){last_error=error.what();return 0;}
#else
    (void)env;(void)handle;(void)matrix;last_error="Native rigid-copy requires linked OCCT shape";return 0;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_release(JNIEnv*,jclass,jlong handle){
#ifdef FREECAD_OCC_AVAILABLE
    std::lock_guard<std::mutex> lock(records_mutex);records.erase(handle);
#else
    (void)handle;
#endif
}
