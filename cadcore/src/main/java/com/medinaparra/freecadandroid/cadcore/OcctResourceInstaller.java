package com.medinaparra.freecadandroid.cadcore;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Installs immutable OCCT resource folders from AAR assets/occt into app storage. */
final class OcctResourceInstaller {
    private static final String[] REQUIRED = {
            "SHMessage", "XSMessage", "StdResource", "XSTEPResource"
    };

    private OcctResourceInstaller() { }

    static File install(Context context) throws CadCoreException {
        if (context == null) throw new CadCoreException("CONTEXT_MISSING",
                "Android context is required to install OCCT resources");
        File root = new File(context.getFilesDir(), "occt_resources_v8_0_0");
        File marker = new File(root, ".installed");
        if (marker.isFile() && complete(root)) return root;
        if (!root.exists() && !root.mkdirs()) {
            throw new CadCoreException("RESOURCE_DIR_FAILED",
                    "Could not create OCCT resource directory");
        }
        AssetManager assets=context.getAssets();
        try {
            for(String directory:REQUIRED){
                String assetPath="occt/"+directory;
                String[] children=assets.list(assetPath);
                if(children==null||children.length==0){
                    throw new CadCoreException("OCCT_ASSET_MISSING",
                            "Missing packaged OCCT resource folder "+assetPath);
                }
                copyTree(assets,assetPath,new File(root,directory));
            }
            try(FileOutputStream output=new FileOutputStream(marker)){
                output.write("OCCT V8_0_0\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                output.getFD().sync();
            }
        } catch(CadCoreException error){throw error;}
        catch(Exception error){
            throw new CadCoreException("OCCT_RESOURCE_INSTALL_FAILED",
                    "Could not install OCCT Android resources",error);
        }
        if(!complete(root))throw new CadCoreException("OCCT_RESOURCE_INCOMPLETE",
                "Installed OCCT resources are incomplete");
        return root;
    }

    private static void copyTree(AssetManager assets,String assetPath,File destination)throws Exception{
        String[] children=assets.list(assetPath);
        if(children!=null&&children.length>0){
            if(!destination.exists()&&!destination.mkdirs())throw new IllegalStateException("Could not create "+destination);
            for(String child:children)copyTree(assets,assetPath+"/"+child,new File(destination,child));
            return;
        }
        File parent=destination.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IllegalStateException("Could not create "+parent);
        try(InputStream input=assets.open(assetPath);FileOutputStream output=new FileOutputStream(destination)){
            byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))>=0)if(count>0)output.write(buffer,0,count);
            output.getFD().sync();
        }
    }

    private static boolean complete(File root){
        for(String directory:REQUIRED){File value=new File(root,directory);if(!value.isDirectory())return false;String[] files=value.list();if(files==null||files.length==0)return false;}
        return true;
    }
}
