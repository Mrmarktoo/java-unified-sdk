package cn.leancloud;

import cn.leancloud.ops.Utils;
import cn.leancloud.utils.LogUtil;
import cn.leancloud.utils.StringUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ObjectTypeAdapter implements ObjectSerializer, ObjectDeserializer{
  private static AVLogger LOGGER = LogUtil.getLogger(ObjectTypeAdapter.class);
  private static final String KEY_VERSION = "_version";
  private static final String DEFAULT_VERSION = "5";

  public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType,
                    int features) throws IOException {
    this.write(serializer, object, fieldName, fieldType);
  }

  public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType) throws IOException {
    AVObject avObject = (AVObject)object;
    SerializeWriter writer = serializer.getWriter();
    writer.write('{');

    // for 1.1.70.android fastjson
    writer.write(' ');
    writer.writeFieldName(KEY_VERSION, false);
    writer.writeString(DEFAULT_VERSION);
    writer.write(',');
    writer.writeFieldName(AVObject.KEY_CLASSNAME, false);
    writer.writeString(avObject.getClassName());
    writer.write(',');
    writer.writeFieldName("serverData", false);
    writer.write(JSON.toJSONString(avObject.serverData, ObjectValueFilter.instance, SerializerFeature.WriteClassName,
            SerializerFeature.DisableCircularReferenceDetect));

//    try {
//      // for 1.2.x fastjson
//      SerializeWriter.class.getDeclaredMethod("writeFieldName", String.class, String.class);
//      writer.writeFieldValue(' ', AVObject.KEY_CLASSNAME, avObject.getClassName());
//      writer.writeFieldValue(',', "serverData",
//              JSON.toJSONString(avObject.serverData, ObjectValueFilter.instance, SerializerFeature.WriteClassName,
//                      SerializerFeature.DisableCircularReferenceDetect));
//    } catch (NoSuchMethodException ex) {
//      // for 1.1.70.android fastjson
//      writer.write(' ');
//      writer.writeFieldName(AVObject.KEY_CLASSNAME, false);
//      writer.writeString(avObject.getClassName());
//      writer.write(',');
//      writer.writeFieldName("serverData", false);
//      writer.write(JSON.toJSONString(avObject.serverData, ObjectValueFilter.instance, SerializerFeature.WriteClassName,
//              SerializerFeature.DisableCircularReferenceDetect));
//    }
    writer.write('}');
  }

  /**
   *
   * @param parser
   * @param type
   * @param fieldName
   * @return
   *
   * @since 1.8+
   */
  public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
    String className = "";
    JSONObject serverJson = null;
    JSONObject jsonObject = parser.parseObject();
    if (jsonObject.containsKey(KEY_VERSION)) {
      // 5.x version
      className = (String) jsonObject.get(AVObject.KEY_CLASSNAME);
      if (jsonObject.containsKey("serverData")) {
        serverJson = jsonObject.getJSONObject("serverData");
      } else {
        serverJson = jsonObject;
      }
    } else if (jsonObject.containsKey("_type")) {
      // android sdk output
      // { "@type":"com.example.avoscloud_demo.Student","objectId":"5bff468944d904005f856849","updatedAt":"2018-12-08T09:53:05.008Z","createdAt":"2018-11-29T01:53:13.327Z","className":"Student","serverData":{"@type":"java.util.concurrent.ConcurrentHashMap","name":"Automatic Tester's Dad","course":["Math","Art"],"age":20}}
      jsonObject.remove("_type");
      className = (String) jsonObject.get(AVObject.KEY_CLASSNAME);
      if (jsonObject.containsKey("serverData")) {
        serverJson = jsonObject.getJSONObject("serverData");
        serverJson.remove("_type");

        jsonObject.remove("serverData");
        jsonObject.putAll(serverJson);
      }
      serverJson = jsonObject;
    } else {
      // leancloud server response.
      serverJson = jsonObject;
    }
    AVObject obj;
    if (type.toString().endsWith(AVFile.class.getCanonicalName())) {
      obj = new AVFile();
    } else if (type.toString().endsWith(AVUser.class.getCanonicalName())) {
      obj = new AVUser();
    } else if (!StringUtil.isEmpty(className)){
      obj = new AVObject(className);
    } else {
      obj = new AVObject();
    }
    Map<String, Object> innerMap = serverJson.getInnerMap();
    for (String k: innerMap.keySet()) {
      Object v = innerMap.get(k);
      if (v instanceof String || v instanceof Number || v instanceof Boolean || v instanceof Byte || v instanceof Character) {
        // primitive type
        obj.serverData.put(k, v);
      } else if (v instanceof Map) {
        obj.serverData.put(k, Utils.getObjectFrom(v));
      } else if (v instanceof Collection) {
        obj.serverData.put(k, Utils.getObjectFrom(v));
      } else if (null != v) {
        obj.serverData.put(k, v);
      }
    }
    return (T) obj;
  }

  public int getFastMatchToken() {
    return JSONToken.LBRACKET;
  }
}
