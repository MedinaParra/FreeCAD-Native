"""Robust App-layer extensions for FreeCAD Android."""
from __future__ import annotations
import ast, copy, math

_MAX_EXPRESSION_CHARS = 4096
_MAX_EXPRESSION_NODES = 128
_MAX_POWER_ABS = 32.0

_TYPES = {
 "App::PropertyString":"", "App::PropertyBool":False, "App::PropertyInteger":0,
 "App::PropertyFloat":0.0, "App::PropertyLength":0.0, "App::PropertyDistance":0.0,
 "App::PropertyAngle":0.0, "App::PropertyPercent":0.0, "App::PropertyVector":None,
 "App::PropertyPlacement":None, "App::PropertyLink":None,
 "App::PropertyLinkSub":None, "App::PropertyLinkList":(),
 "App::PropertyStringList":(), "App::PropertyFloatList":(),
 "App::PropertyIntegerList":(),
}
_NON_GEOM={"App::DocumentObjectGroup","App::Feature","App::FeaturePython"}

class Quantity(float):
 def __new__(cls,value=0.0,unit=""):
  obj=float.__new__(cls,float(value)); obj.Unit=str(unit); return obj
 @property
 def Value(self): return float(self)
 @property
 def UserString(self): return f"{float(self):g} {self.Unit}".strip()
 def getValueAs(self,unit): return Quantity(self,unit)

class Units: Quantity=Quantity

class ParamGet:
 _stores={}
 def __init__(self,path): self.path=str(path); self._data=self._stores.setdefault(self.path,{})
 def GetString(self,k,d=""): return str(self._data.get(str(k),d))
 def SetString(self,k,v): self._data[str(k)]=str(v)
 def GetBool(self,k,d=False): return bool(self._data.get(str(k),d))
 def SetBool(self,k,v): self._data[str(k)]=bool(v)
 def GetInt(self,k,d=0): return int(self._data.get(str(k),d))
 def SetInt(self,k,v): self._data[str(k)]=int(v)
 def GetFloat(self,k,d=0.0): return float(self._data.get(str(k),d))
 def SetFloat(self,k,v): self._data[str(k)]=float(v)
 def GetContents(self): return sorted(self._data.items())

def _install_vectors(app):
 V=app.Vector
 def vec(v): return v if isinstance(v,V) else V(*v)
 V.__eq__=lambda s,o:isinstance(o,V) and math.isclose(s.x,o.x,abs_tol=1e-12) and math.isclose(s.y,o.y,abs_tol=1e-12) and math.isclose(s.z,o.z,abs_tol=1e-12)
 V.isEqual=lambda s,o,tol=1e-7: isinstance(o,V) and (s-o).Length<=abs(float(tol))
 V.__add__=lambda s,o: V(s.x+vec(o).x,s.y+vec(o).y,s.z+vec(o).z)
 V.__sub__=lambda s,o: V(s.x-vec(o).x,s.y-vec(o).y,s.z-vec(o).z)
 V.__neg__=lambda s: V(-s.x,-s.y,-s.z)
 V.__mul__=lambda s,n: V(s.x*float(n),s.y*float(n),s.z*float(n))
 V.__rmul__=V.__mul__
 def divide(s,n):
  divisor=float(n)
  if not math.isfinite(divisor) or abs(divisor)<=1e-15: raise ZeroDivisionError("Vector division requires a finite non-zero divisor")
  return s*(1.0/divisor)
 V.__truediv__=divide
 V.dot=lambda s,o:s.x*vec(o).x+s.y*vec(o).y+s.z*vec(o).z
 V.cross=lambda s,o:V(s.y*vec(o).z-s.z*vec(o).y,s.z*vec(o).x-s.x*vec(o).z,s.x*vec(o).y-s.y*vec(o).x)
 def normalize(s):
  length=s.Length
  if length>1e-15: s.x/=length; s.y/=length; s.z/=length
  return length
 V.normalize=normalize
 V.normalized=lambda s:(lambda r:(r.normalize(),r)[1])(s.copy())
 def get_angle(s,o):
  other=vec(o); denominator=s.Length*other.Length
  if denominator<=1e-15: raise ValueError("Cannot compute an angle with a null vector")
  return math.acos(max(-1.0,min(1.0,s.dot(other)/denominator)))
 V.getAngle=get_angle
 V.add=lambda s,o:s+o; V.sub=lambda s,o:s-o; V.multiply=lambda s,n:s*n

def _coerce(app,t,v):
 if t=="App::PropertyString": return str(v)
 if t=="App::PropertyBool": return bool(v)
 if t=="App::PropertyInteger": return int(v)
 if t in {"App::PropertyFloat","App::PropertyLength","App::PropertyDistance","App::PropertyAngle","App::PropertyPercent"}:
  value=float(v)
  if not math.isfinite(value): raise ValueError(f"{t} requires a finite value")
  return value
 if t=="App::PropertyVector": return v.copy() if isinstance(v,app.Vector) else app.Vector(*v)
 if t=="App::PropertyPlacement":
  if not isinstance(v,app.Placement): raise TypeError("Expected FreeCAD.Placement")
  return v.copy()
 if t in {"App::PropertyLink","App::PropertyLinkSub"}:
  if v is not None and not isinstance(v,app.DocumentObject): raise TypeError("Expected DocumentObject")
  return v
 if t=="App::PropertyLinkList":
  values=list(v)
  if any(not isinstance(x,app.DocumentObject) for x in values): raise TypeError("Expected DocumentObject list")
  return values
 if t=="App::PropertyStringList": return [str(x) for x in v]
 if t=="App::PropertyFloatList":
  values=[float(x) for x in v]
  if any(not math.isfinite(x) for x in values): raise ValueError("App::PropertyFloatList requires finite values")
  return values
 if t=="App::PropertyIntegerList": return [int(x) for x in v]
 raise NotImplementedError(f"Property type {t!r} is not supported")

def _links(obj):
 result=[]
 for value in (getattr(obj,"Base",None),getattr(obj,"Tool",None)):
  if value is not None: result.append(value)
 for name,info in getattr(obj,"_fc_defs",{}).items():
  value=obj._fc_values.get(name)
  if info["type"] in {"App::PropertyLink","App::PropertyLinkSub"} and value is not None: result.append(value)
  elif info["type"]=="App::PropertyLinkList": result.extend(value or [])
 if obj.TypeId=="App::DocumentObjectGroup": result.extend(obj._fc_group)
 return list(dict.fromkeys(result))

def _parse_expression(text):
 source=str(text)
 if not source.strip(): raise ValueError("Expression must not be empty")
 if len(source)>_MAX_EXPRESSION_CHARS: raise ValueError("Expression exceeds the 4096 character limit")
 tree=ast.parse(source,mode="eval")
 if sum(1 for _ in ast.walk(tree))>_MAX_EXPRESSION_NODES:
  raise ValueError("Expression is too complex")
 return tree

def _eval(doc,text,tree=None):
 tree=_parse_expression(text) if tree is None else tree
 def run(n):
  if isinstance(n,ast.Expression): return run(n.body)
  if isinstance(n,ast.Constant) and isinstance(n.value,(int,float)) and not isinstance(n.value,bool): return n.value
  if isinstance(n,ast.Name):
   if n.id=="pi": return math.pi
   obj=doc.getObject(n.id)
   if obj is None: raise NameError(n.id)
   return obj
  if isinstance(n,ast.Attribute):
   if n.attr.startswith("_"): raise ValueError("Private attributes are not allowed in expressions")
   return getattr(run(n.value),n.attr)
  if isinstance(n,ast.UnaryOp): return -run(n.operand) if isinstance(n.op,ast.USub) else +run(n.operand)
  if isinstance(n,ast.BinOp):
   a,b=run(n.left),run(n.right)
   if isinstance(n.op,ast.Add): result=a+b
   elif isinstance(n.op,ast.Sub): result=a-b
   elif isinstance(n.op,ast.Mult): result=a*b
   elif isinstance(n.op,ast.Div): result=a/b
   elif isinstance(n.op,ast.Pow):
    if not isinstance(b,(int,float)) or not math.isfinite(float(b)) or abs(float(b))>_MAX_POWER_ABS:
     raise ValueError("Expression exponent is outside the safe range")
    result=a**b
   else: raise ValueError("Unsupported expression operator")
   if isinstance(result,complex) or (isinstance(result,(int,float)) and not math.isfinite(float(result))):
    raise ValueError("Expression produced a non-finite result")
   return result
  raise ValueError("Unsupported expression")
 return run(tree)

def _expression_order(doc):
 entries={}
 for obj in doc.Objects:
  for prop,text in obj._fc_expr.items(): entries[(obj,prop)]=(text,_parse_expression(text))
 state={}; ordered=[]
 def visit(key):
  current=state.get(key,0)
  if current==2:return
  if current==1:raise ValueError(f"Expression dependency cycle at {key[0].Name}.{key[1]}")
  state[key]=1
  tree=entries[key][1]
  for node in ast.walk(tree):
   if not isinstance(node,ast.Attribute) or not isinstance(node.value,ast.Name):continue
   dependency_object=doc.getObject(node.value.id)
   dependency=(dependency_object,node.attr)
   if dependency_object is not None and dependency in entries:visit(dependency)
  state[key]=2;ordered.append((key[0],key[1],entries[key][0],tree))
 for key in entries:visit(key)
 return ordered

def install(app):
 if getattr(app,"_android_core_model_installed",False): return app
 app._android_core_model_installed=True; _install_vectors(app)
 O,D=app.DocumentObject,app.Document
 oi,og,os,da,dr=O.__init__,O.__getattr__,O.__setattr__,D.addObject,D.removeObject
 O._internal_names=set(O._internal_names)|{"_fc_defs","_fc_values","_fc_group","_fc_expr","_fc_modes","_fc_touched"}
 def init_obj(self,*a,**k):
  oi(self,*a,**k)
  object.__setattr__(self,"_fc_defs",{}); object.__setattr__(self,"_fc_values",{})
  object.__setattr__(self,"_fc_group",[]); object.__setattr__(self,"_fc_expr",{})
  object.__setattr__(self,"_fc_modes",{}); object.__setattr__(self,"_fc_touched",True)
 def get_attr(self,name):
  self._assert_live()
  if name in self._fc_values:return self._fc_values[name]
  return og(self,name)
 def set_attr(self,name,value):
  self._assert_live()
  if name in self.__dict__.get("_fc_defs",{}):
   if self._fc_modes.get(name)==1: raise AttributeError(f"{name} is read-only")
   coerced=_coerce(app,self._fc_defs[name]["type"],value)
   prop_type=self._fc_defs[name]["type"]
   links=coerced if prop_type=="App::PropertyLinkList" else [coerced]
   if prop_type in {"App::PropertyLink","App::PropertyLinkSub","App::PropertyLinkList"}:
    if any(link is not None and link._document is not self._document for link in links):
     raise ValueError("Document links cannot cross document boundaries")
   self._fc_values[name]=coerced; self.touch(); return
  os(self,name,value)
  if name=="Label" and "_fc_touched" in self.__dict__: self.touch()
 O.__init__,O.__getattr__,O.__setattr__=init_obj,get_attr,set_attr
 def add_prop(self,t,name,group="",doc="",attr=0,readonly=False,hidden=False):
  self._assert_live()
  t,name=str(t),str(name)
  if t not in _TYPES: raise NotImplementedError(f"Property type {t!r} is not supported")
  if not name.isidentifier(): raise ValueError("Invalid property name")
  if name in self._fc_defs or name in self._properties:return self
  default=_TYPES[t]
  if t=="App::PropertyVector":default=app.Vector()
  elif t=="App::PropertyPlacement":default=app.Placement()
  elif isinstance(default,tuple):default=list(default)
  self._fc_defs[name]={"type":t,"group":str(group),"doc":str(doc),"attr":int(attr),"hidden":bool(hidden)}
  self._fc_values[name]=default; self._fc_modes[name]=1 if readonly else 0; self.touch(); return self
 def touch(self):
  self._assert_live()
  object.__setattr__(self,"_fc_touched",True)
  for obj in self.InList: object.__setattr__(obj,"_fc_touched",True)
 def set_expr(self,prop,text):
  self._assert_live()
  prop=str(prop)
  if text is None:self._fc_expr.pop(prop,None)
  else:
   if prop not in self.PropertiesList:raise AttributeError(prop)
   _parse_expression(text)
   self._fc_expr[prop]=str(text)
  self.touch()
 def group_add(self,obj):
  self._assert_live();obj._assert_live()
  if self.TypeId!="App::DocumentObjectGroup":raise TypeError("Not a group")
  if obj._document is not self._document:raise ValueError("Different document")
  if obj not in self._fc_group:self._fc_group.append(obj);self.touch()
  return obj
 O.addProperty=add_prop
 O.removeProperty=lambda s,n:(s._fc_defs.pop(str(n),None),s._fc_values.pop(str(n),None),s._fc_expr.pop(str(n),None),s.touch())
 O.getPropertyByName=lambda s,n:getattr(s,str(n))
 O.getTypeIdOfProperty=lambda s,n:s._fc_defs.get(str(n),{}).get("type","")
 O.getGroupOfProperty=lambda s,n:s._fc_defs.get(str(n),{}).get("group","")
 O.getDocumentationOfProperty=lambda s,n:s._fc_defs.get(str(n),{}).get("doc","")
 O.setEditorMode=lambda s,n,m:s._fc_modes.__setitem__(str(n),int(m))
 O.getEditorMode=lambda s,n:s._fc_modes.get(str(n),0)
 O.touch=touch;O.purgeTouched=lambda s:object.__setattr__(s,"_fc_touched",False)
 O.isTouched=lambda s:s._fc_touched;O.setExpression=set_expr;O.getExpression=lambda s,p:s._fc_expr.get(str(p))
 O.addObject=group_add;O.removeObject=lambda s,o:(s._fc_group.remove(o),s.touch()) if o in s._fc_group else None
 O.isDerivedFrom=lambda s,t:s.TypeId==str(t) or str(t)=="App::DocumentObject"
 O.PropertiesList=property(lambda s:sorted(set(s._properties)|set(s._fc_defs)|{"Label","Placement","Visibility"}))
 O.State=property(lambda s:["Touched"] if s._fc_touched else [])
 O.Group=property(lambda s:list(s._fc_group));O.OutList=property(_links)
 O.InList=property(lambda s:[o for o in s._document.Objects if s in _links(o)])
 di=D.__init__
 def init_doc(self,*a,**k):
  di(self,*a,**k);self._fc_tx=None;self._fc_undo=[];self._fc_redo=[];self._fc_tid=0
 D.__init__=init_doc
 def add_obj(self,t,name):
  self._assert_open()
  t=str(t)
  if t in _NON_GEOM:
   name=self._unique_name(str(name));obj=app.DocumentObject(self,t,name,0)
   self._objects.append(obj);self._by_name[name]=obj;return obj
  if t=="PartDesign::Feature":t="Part::Feature"
  obj=da(self,t,name);obj.touch();return obj
 D.addObject=add_obj
 def remove_obj(self,name_or_object):
  self._assert_open()
  target=name_or_object if isinstance(name_or_object,app.DocumentObject) else self._by_name.get(str(name_or_object))
  if target is None:return None
  dependents=[o.Name for o in self.Objects if o is not target and target in _links(o)]
  if dependents:raise RuntimeError(f"Cannot remove {target.Name!r}; referenced by {', '.join(dependents)}")
  return dr(self,target)
 D.removeObject=remove_obj
 def snapshot(doc):
  return [{"name":o.Name,"label":o.Label,"primitive":copy.deepcopy(o._properties),
   "custom":copy.deepcopy(o._fc_values),"expr":dict(o._fc_expr),
   "placement":o.Placement.copy(),"visible":o.Visibility} for o in doc.Objects]
 def restore(doc,data):
  by={x["name"]:x for x in data}
  for o in doc.Objects:
   if o.Name not in by:continue
   x=by[o.Name];o.Label=x["label"];o.Placement=x["placement"];o.Visibility=x["visible"]
   for k,v in x["primitive"].items():setattr(o,k,v)
   for k,v in x["custom"].items():
    if k in o._fc_defs:o._fc_values[k]=v
   o._fc_expr=dict(x["expr"]);o.touch()
  doc.recompute()
 def recompute(doc,*a,**k):
  doc._assert_open()
  ordered=_expression_order(doc)
  previous=[(o,p,copy.deepcopy(getattr(o,p))) for o,p,_,_ in ordered]
  try:
   for o,p,e,tree in ordered:setattr(o,p,_eval(doc,e,tree))
   for o in doc.Objects:
    if o.TypeId in _NON_GEOM:continue
    if o.TypeId=="Part::Feature" and o._shape_spec is None:continue
    o._ensure_materialized();o._sync_placement()
    if o._id:app._native.set_visibility(doc._id,o._id,o.Visibility)
   app._native.set_active_document(doc._id);ok=bool(app._native.recompute(doc._id))
   if not ok:raise RuntimeError(app._native.last_error(doc._id) or "Native recompute failed")
  except Exception:
   for o,p,value in reversed(previous):setattr(o,p,value)
   raise
  for o in doc.Objects:o.purgeTouched()
  return True
 D.recompute=recompute
 D.findObjects=lambda s,Type=None,Label=None:[o for o in s.Objects if (not Type or o.isDerivedFrom(Type)) and (not Label or o.Label==Label)]
 D.getObjectsByLabel=lambda s,l:[o for o in s.Objects if o.Label==str(l)]
 def open_tx(s,name="Transaction"):
  if s._fc_tx is not None:raise RuntimeError("Transaction already open")
  s._fc_tid+=1;s._fc_tx=(s._fc_tid,str(name),snapshot(s));return s._fc_tid
 def commit(s):
  if s._fc_tx is None:return 0
  tid,_,before=s._fc_tx;s._fc_undo.append(before);s._fc_redo.clear();s._fc_tx=None;return tid
 def abort(s):
  if s._fc_tx is not None:
   _,_,before=s._fc_tx;s._fc_tx=None;restore(s,before)
 def undo(s):
  if not s._fc_undo:return False
  s._fc_redo.append(snapshot(s));restore(s,s._fc_undo.pop());return True
 def redo(s):
  if not s._fc_redo:return False
  s._fc_undo.append(snapshot(s));restore(s,s._fc_redo.pop());return True
 D.openTransaction=open_tx;D.commitTransaction=commit;D.abortTransaction=abort;D.undo=undo;D.redo=redo
 D.clearUndos=lambda s:(s._fc_undo.clear(),s._fc_redo.clear())
 D.TransactionID=property(lambda s:s._fc_tid);D.UndoCount=property(lambda s:len(s._fc_undo));D.RedoCount=property(lambda s:len(s._fc_redo))
 app.Units=Units();app.Quantity=Quantity;app.ParamGet=ParamGet;app.GuiUp=False
 app.__all__.extend(["Units","Quantity","ParamGet","GuiUp"])
 return app
