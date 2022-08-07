package org.word.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.word.model.ModelAttr;
import org.word.model.Request;
import org.word.model.Response;
import org.word.model.Table;
import org.word.service.WordService;
import org.word.utils.ClassType;
import org.word.utils.JsonUtils;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * @Author XiuYin.Cui
 * @Date 2018/1/12
 **/
@SuppressWarnings({"unchecked", "rawtypes"})
@Slf4j
@Service
public class WordServiceImpl implements WordService {

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public Map<String, Object> tableList(String swaggerUrl) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String jsonStr = restTemplate.getForObject(swaggerUrl, String.class);
            resultMap = tableListFromString(jsonStr);
            log.debug(JsonUtils.writeJsonStr(resultMap));
        } catch (Exception e) {
            log.error("parse error", e);
        }
        return resultMap;
    }

    @Override
    public Map<String, Object> tableListFromString(String jsonStr) {
        Map<String, Object> resultMap = new HashMap<>();
        List<Table> result = new ArrayList<>();
        try {
            Map<String, Object> map = getResultFromString(result, jsonStr);
            Map<String, List<Table>> tableMap = result.stream().parallel().collect(Collectors.groupingBy(Table::getTitle));
            resultMap.put("tableMap", new TreeMap<>(tableMap));
            resultMap.put("info", map.get("info"));

            log.debug(JsonUtils.writeJsonStr(resultMap));
        } catch (Exception e) {
            log.error("parse error", e);
        }
        return resultMap;
    }

    @Override
    public Map<String, Object> tableList(MultipartFile jsonFile) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String jsonStr = new String(jsonFile.getBytes());
            resultMap = tableListFromString(jsonStr);
            log.debug(JsonUtils.writeJsonStr(resultMap));
        } catch (Exception e) {
            log.error("parse error", e);
        }
        return resultMap;
    }

    private Map<String, Object> getResultFromString(List<Table> result, String jsonStr) throws IOException {
        // convert JSON string to Map
        Map<String, Object> map = JsonUtils.readValue(jsonStr, HashMap.class);

        Map<String, Object> commonInfos = parseCommonInfo(map);

        //解析model
        Map<String, ModelAttr> definitionMap = parseDefinitions(map);

        //解析paths
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) map.get("paths");

        //获取全局请求参数格式作为默认请求参数格式
        List<String> defaultConsumes = (List) map.getOrDefault("consumes",
                Lists.newArrayList("application/json"));

        //获取全局响应参数格式作为默认响应参数格式
        List<String> defaultProduces = (List) map.getOrDefault("produces",
                Lists.newArrayList("application/json"));

        if (paths != null) {

            Iterator<Entry<String, Map<String, Object>>> it = paths.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Map<String, Object>> path = it.next();

                // 0. 获取该路由下所有请求方式的公共参数
                Map<String, Object> methods = (Map<String, Object>) path.getValue();
                List<LinkedHashMap> commonParameters = (ArrayList) methods.get("parameters");

                Iterator<Entry<String, Object>> it2 = path.getValue().entrySet().iterator();
                // 1.请求路径
                String url = path.getKey();
                url = commonInfos.get("basePath") + url;

                while (it2.hasNext()) {
                    Entry<String, Object> request = it2.next();

                    // 2.请求方式，类似为 get,post,delete,put 这样
                    String requestType = request.getKey();

                    if ("parameters".equals(requestType)) {
                        continue;
                    }

                    Map<String, Object> content = (Map<String, Object>) request.getValue();

                    // 4. 大标题（类说明）
                    String title = String.valueOf(((List) content.get("tags")).get(0));

                    // 5.小标题 （方法说明）
                    String tag = String.valueOf(content.getOrDefault("operationId", ""));

                    // 6.接口描述
                    String description = String.valueOf(content.getOrDefault("description", ""));

                    tag = StringUtils.isEmpty(tag) ? description : tag;

                    // 7.请求参数格式，类似于 multipart/form-data
                    String requestForm = "";
                    List<String> consumes = (List) content.get("consumes");
                    if (consumes != null && consumes.size() > 0) {
                        requestForm = StringUtils.join(consumes, ",");
                    } else {
                        requestForm = StringUtils.join(defaultConsumes, ",");
                    }

                    // 8.返回参数格式，类似于 application/json
                    String responseForm = "";
                    List<String> produces = (List) content.get("produces");
                    if (produces != null && produces.size() > 0) {
                        responseForm = StringUtils.join(produces, ",");
                    } else {
                        responseForm = StringUtils.join(defaultProduces, ",");
                    }

                    // 9. 请求体
                    List<LinkedHashMap> parameters = (ArrayList) content.get("parameters");

                    if (!CollectionUtils.isEmpty(parameters)) {
                        if (commonParameters != null) {
                            parameters.addAll(commonParameters);
                        }
                    } else {
                        if (commonParameters != null) {
                            parameters = commonParameters;
                        }
                    }

                    // 10.返回体
                    Map<String, Object> responses = (LinkedHashMap) content.get("responses");

                    Map<String, List<Request>> requestMap = processRequestList(map, parameters, definitionMap);

                    //封装Table
                    Table table = new Table();

                    table.setTitle(title);
                    table.setUrl(url);
                    table.setTag(tag);
                    table.setDescription(description);
                    table.setRequestForm(requestForm);
                    table.setResponseForm(responseForm);
                    table.setRequestType(requestType);
                    table.setPathList(requestMap.get("path"));
                    table.setQueryList(requestMap.get("query"));
                    table.setBodyList(requestMap.get("body"));
                    table.setResponseList(processResponseCodeList(responses));

                    // 取出来状态是200时的返回值
                    Map<String, Object> obj = (Map<String, Object>) responses.get("200");
                    if (obj != null && obj.get("schema") != null) {
                        table.setModelAttr(processResponseModelAttrs(map, obj, definitionMap));
                    }

                    //示例
                    table.setRequestParam(processRequestParam(table.getBodyList()));
                    table.setResponseParam(processResponseParam(map, obj, definitionMap));

                    result.add(table);
                }
            }
        }
        return map;
    }

    private Map<String, Object> parseCommonInfo(Map<String, Object> map) {
        Map<String, Object> commonInfos = new HashMap<>();
        commonInfos.put("basePath", map.getOrDefault("basePath", ""));
        return commonInfos;
    }

    /**
     * 处理请求参数列表
     *
     * @param parameters
     * @param definitinMap
     * @return
     */
    private Map<String, List<Request>> processRequestList(Map<String, Object> map, List<LinkedHashMap> parameters, Map<String, ModelAttr> definitinMap) {
        Map<String, Map<String, Object>> definitions = (Map<String, Map<String, Object>>) map.get("definitions");
        Map<String, List<Request>> requestMap = new HashMap<>();
        {
            requestMap.put("path", new ArrayList<>());
            requestMap.put("query", new ArrayList<>());
            requestMap.put("body", new ArrayList<>());
        }
        if (!CollectionUtils.isEmpty(parameters)) {
            for (Map<String, Object> param : parameters) {
                Object in = param.get("in");
                Request request = new Request();
                request.setName(String.valueOf(param.get("name")));
                request.setType(param.get("type") == null ? "object" : param.get("type").toString());
                if (param.get("format") != null) {
                    request.setType(request.getType() + "(" + param.get("format") + ")");
                }
                request.setParamType(String.valueOf(in));
                // 考虑对象参数类型
                if ("body".equals(in)) {
                    request.setType(String.valueOf(in));
                    Map<String, Object> schema = (Map) param.get("schema");
                    Object ref = schema.get("$ref");
                    // 数组情况另外处理
                    if (schema.get("type") != null && "array".equals(schema.get("type"))) {
                        ref = ((Map) schema.get("items")).get("$ref");
                        if (ref != null) {
                            String refName = ((Map) schema.get("items")).get("$ref").toString();
                            //截取 #/definitions/ 后面的
                            String clsName = refName.substring(14);
                            request.setType("array:" + clsName);
                        } else if (((Map) schema.get("items")).containsKey("type")) {
                            request.setType("array:" + ((Map) schema.get("items")).get("type"));
                        } else {
                            request.setType("array");
                        }
                    }
                    if (ref != null) {
                        request.setModelAttr(definitinMap.get(ref));
                    }
                    if (schema.get("allOf") != null) {
                        List<Map<String, Object>> items = (List<Map<String, Object>>) schema.get("allOf");
                        for (Map<String, Object> entry : items) {
                            if (entry.get("$ref") != null) {
                                String refName = entry.get("$ref").toString();
                                request.setModelAttr(definitinMap.getOrDefault(refName, new ModelAttr()));
                            } else if (entry.get("properties") != null) {
                                Map<String, Object> modeProperties1 = (Map<String, Object>) entry.get("properties");
                                List<ModelAttr> modelAttrList = getModelAttrs(definitions, definitinMap, new ModelAttr(), modeProperties1);
                                ModelAttr modelAttr = request.getModelAttr();
                                if (modelAttr == null) {
                                    request.setModelAttr(new ModelAttr());
                                }
                                List<ModelAttr> attrList = request.getModelAttr().getProperties();

                                for (ModelAttr newModel : modelAttrList) {
                                    for (int j = 0; j < attrList.size(); j++) {
                                        if (newModel.getName().equals(attrList.get(j).getName())) {
                                            attrList.set(j, newModel);
                                            break;
                                        }
                                    }
                                    attrList.add(newModel);
                                }
                            }
                        }
                    }
                } else if ("path".equals(in) || "query".equals(in)){
                    if (param.containsKey("items")) {
                        Map<String, Object> items = (Map<String, Object>) param.get("items");
                        if (items.containsKey("type")) {
                            request.setType("array:" + items.get("type"));
                        }
                    }
                }
                // 是否必填
                request.setRequire(false);
                if (param.get("required") != null) {
                    request.setRequire((Boolean) param.get("required"));
                }
                // 参数说明
                request.setRemark(String.valueOf(param.getOrDefault("description", "")));

                List<Request> requestList = requestMap.get(String.valueOf(in));
                if (CollectionUtils.isEmpty(requestList)) {
                    requestList = new ArrayList<>();
                    requestMap.put(String.valueOf(in), requestList);
                }
                requestList.add(request);
            }
        }
        return requestMap;
    }


    /**
     * 处理返回码列表
     *
     * @param responses 全部状态码返回对象
     * @return
     */
    private List<Response> processResponseCodeList(Map<String, Object> responses) {
        List<Response> responseList = new ArrayList<>();
        Iterator<Map.Entry<String, Object>> resIt = responses.entrySet().iterator();
        while (resIt.hasNext()) {
            Map.Entry<String, Object> entry = resIt.next();
            Response response = new Response();
            // 状态码 200 201 401 403 404 这样
            response.setName(entry.getKey());
            LinkedHashMap<String, Object> statusCodeInfo = (LinkedHashMap) entry.getValue();
            response.setDescription(String.valueOf(statusCodeInfo.get("description")));
            Object schema = statusCodeInfo.get("schema");
            if (schema != null) {
                Object originalRef = ((LinkedHashMap) schema).get("originalRef");
                response.setRemark(originalRef == null ? "" : originalRef.toString());
            }
            responseList.add(response);
        }
        return responseList;
    }

    /**
     * 处理返回属性列表
     *
     * @param responseObj
     * @param definitinMap
     * @return
     */
    private ModelAttr processResponseModelAttrs(Map<String, Object> map, Map<String, Object> responseObj, Map<String, ModelAttr> definitinMap) {
        Map<String, Map<String, Object>> definitions = (Map<String, Map<String, Object>>) map.get("definitions");

        Map<String, Object> schema = (Map<String, Object>) responseObj.get("schema");
        String type = (String) schema.get("type");
        String ref = null;
        //数组
        if ("array".equals(type)) {
            Map<String, Object> items = (Map<String, Object>) schema.get("items");
            if (items != null && items.get("$ref") != null) {
                ref = (String) items.get("$ref");
            }
        }
        //对象
        if (schema.get("$ref") != null) {
            ref = (String) schema.get("$ref");
        }

        //其他类型
        ModelAttr modelAttr = new ModelAttr();
        modelAttr.setType(StringUtils.defaultIfBlank(type, StringUtils.EMPTY));

        if (StringUtils.isNotBlank(ref) && definitinMap.get(ref) != null) {
            modelAttr = definitinMap.get(ref);
        }

        // allOf
        if (schema.get("allOf") != null) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) schema.get("allOf");
            for (Map<String, Object> entry : items) {
                if (entry.get("$ref") != null) {
                    String refName = entry.get("$ref").toString();
                    modelAttr.getProperties().addAll(definitinMap.getOrDefault(refName, new ModelAttr()).getProperties());
                } else if (entry.get("properties") != null) {
                    Map<String, Object> modeProperties1 = (Map<String, Object>) entry.get("properties");
                    List<ModelAttr> modelAttrList = getModelAttrs(definitions, definitinMap, modelAttr, modeProperties1);
                    List<ModelAttr> attrList = modelAttr.getProperties();
                    for (ModelAttr newModel : modelAttrList) {
                        for (int j = 0; j < attrList.size(); j++) {
                            if (newModel.getName().equals(attrList.get(j).getName())) {
                                attrList.set(j, newModel);
                            }
                        }
                    }
                }
            }
        }

        return modelAttr;
    }

    /**
     * 解析Definition
     *
     * @param map
     * @return
     */
    private Map<String, ModelAttr> parseDefinitions(Map<String, Object> map) {
        Map<String, Map<String, Object>> definitions = (Map<String, Map<String, Object>>) map.get("definitions");
        Map<String, ModelAttr> definitionMap = new HashMap<>(256);
        if (definitions != null) {
            Iterator<String> modelNameIt = definitions.keySet().iterator();
            while (modelNameIt.hasNext()) {
                String modeName = modelNameIt.next();
                getAndPutModelAttr(definitions, definitionMap, modeName);
            }
        }
        return definitionMap;
    }

    /**
     * 递归生成ModelAttr
     * 对$ref类型设置具体属性
     */
    private ModelAttr getAndPutModelAttr(Map<String, Map<String, Object>> swaggerMap, Map<String, ModelAttr> resMap, String modeName) {
        ModelAttr modeAttr;
        if ((modeAttr = resMap.get("#/definitions/" + modeName)) == null) {
            modeAttr = new ModelAttr();
            resMap.put("#/definitions/" + modeName, modeAttr);
        } else if (modeAttr.isCompleted()) {
            return resMap.get("#/definitions/" + modeName);
        }

        Map<String, Object> modeProperties = (Map<String, Object>) swaggerMap.get(modeName).getOrDefault("properties", new HashMap<>());
        // map
        if (swaggerMap.get(modeName).containsKey("additionalProperties")) {
            modeProperties.put("dictionary key (*)", swaggerMap.get(modeName).get("additionalProperties"));
        }
        if (modeProperties.isEmpty()) {
            return null;
        }

        List<ModelAttr> attrList = getModelAttrs(swaggerMap, resMap, modeAttr, modeProperties);
        List allOf = (List) swaggerMap.get(modeName).get("allOf");
        if (allOf != null) {
            for (int i = 0; i < allOf.size(); i++) {
                Map c = (Map) allOf.get(i);
                if (c.get("$ref") != null) {
                    String refName = c.get("$ref").toString();
                    //截取 #/definitions/ 后面的
                    String clsName = refName.substring(14);
                    Map<String, Object> modeProperties1 = (Map<String, Object>) swaggerMap.get(clsName).get("properties");
                    List<ModelAttr> attrList1 = getModelAttrs(swaggerMap, resMap, modeAttr, modeProperties1);
                    if (!attrList1.isEmpty() && !attrList.isEmpty()) {
                        attrList.addAll(attrList1);
                    } else if (attrList.isEmpty() && !attrList1.isEmpty()) {
                        attrList = attrList1;
                    }
                }
            }
        }

        Object title = swaggerMap.get(modeName).get("title");
        Object description = swaggerMap.get(modeName).get("description");
        modeAttr.setClassName(title == null ? "" : title.toString());
        modeAttr.setDescription(description == null ? "" : description.toString());
        modeAttr.setProperties(attrList);
        modeAttr.setExample(attrList.size() != 0 ? "#ref" : swaggerMap.get(modeName).get("example"));
        Object required = swaggerMap.get(modeName).get("required");
        if (Objects.nonNull(required)) {
            if ((required instanceof List) && !CollectionUtils.isEmpty(attrList)) {
                List requiredList = (List) required;
                attrList.stream().filter(m -> requiredList.contains(m.getName())).forEach(m -> m.setRequire(true));
            } else if (required instanceof Boolean) {
                modeAttr.setRequire(Boolean.parseBoolean(required.toString()));
            }
        }
        return modeAttr;
    }

    private List<ModelAttr> getModelAttrs(Map<String, Map<String, Object>> swaggerMap, Map<String, ModelAttr> resMap, ModelAttr modeAttr, Map<String, Object> modeProperties) {
        Iterator<Entry<String, Object>> mIt = modeProperties.entrySet().iterator();

        List<ModelAttr> attrList = new ArrayList<>();

        //解析属性
        while (mIt.hasNext()) {
            Entry<String, Object> mEntry = mIt.next();
            Map<String, Object> attrInfoMap = (Map<String, Object>) mEntry.getValue();
            ModelAttr child = new ModelAttr();
            child.setName(mEntry.getKey());
            child.setType((String) attrInfoMap.get("type"));
            if (attrInfoMap.get("format") != null) {
                child.setType(child.getType() + "(" + attrInfoMap.get("format") + ")");
            }
            child.setType(StringUtils.defaultIfBlank(child.getType(), "object"));

            Object ref = attrInfoMap.get("$ref");
            Object items = attrInfoMap.get("items");
            if (items != null) {
                if (((Map)items).containsKey("type")) {
                    child.setType(child.getType() + ":" + ((Map)items).get("type"));
                }
            }
            if (ref != null || (items != null && (ref = ((Map) items).get("$ref")) != null)) {
                String refName = ref.toString();
                //截取 #/definitions/ 后面的
                String clsName = refName.substring(14);
                modeAttr.setCompleted(true);
                ModelAttr refModel = getAndPutModelAttr(swaggerMap, resMap, clsName);
                if (refModel != null) {
                    child.setProperties(refModel.getProperties());
                }
                child.setType(child.getType() + ":" + clsName);
            } else if (attrInfoMap.containsKey("additionalProperties")) {
                Map<String, Object> subModeProperties = new HashMap<>();
                modeAttr.setCompleted(true);
                if (ClassType.isMap(attrInfoMap.get("additionalProperties"))) {
                    subModeProperties.put("dictionary key (*)", attrInfoMap.get("additionalProperties"));
                    List<ModelAttr> subModelAttrList = getModelAttrs(swaggerMap, resMap, child, subModeProperties);
                    child.setProperties(subModelAttrList);
                }
            }
            child.setDescription((String) attrInfoMap.get("description"));
            attrList.add(child);
        }
        return attrList;
    }

    /**
     * 处理返回值
     *
     * @param responseObj
     * @return
     */
    private String processResponseParam(Map<String, Object> map, Map<String, Object> responseObj, Map<String, ModelAttr> definitinMap) throws JsonProcessingException {
        Map<String, Map<String, Object>> definitions = (Map<String, Map<String, Object>>) map.get("definitions");
        if (responseObj != null && responseObj.get("schema") != null) {
            Map<String, Object> schema = (Map<String, Object>) responseObj.get("schema");
            String type = (String) schema.get("type");
            String ref = null;
            // 数组
            if ("array".equals(type)) {
                Map<String, Object> items = (Map<String, Object>) schema.get("items");
                if (items != null && items.get("$ref") != null) {
                    ref = (String) items.get("$ref");
                }
            }
            // 对象
            if (schema.get("$ref") != null) {
                ref = (String) schema.get("$ref");
            }
            if (StringUtils.isNotEmpty(ref)) {
                ModelAttr modelAttr = definitinMap.get(ref);
                if (modelAttr != null && !CollectionUtils.isEmpty(modelAttr.getProperties())) {
                    Map<String, Object> responseMap = new HashMap<>(8);
                    for (ModelAttr subModelAttr : modelAttr.getProperties()) {
                        responseMap.put(subModelAttr.getName(), getValue(subModelAttr.getType(), subModelAttr));
                    }
                    return JsonUtils.writePrettyJSON(responseMap);
                }
            }

            // allOf
            if (schema.get("allOf") != null) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) schema.get("allOf");
                Map<String, Object> responseMap = new HashMap<>();
                ModelAttr modelAttr = new ModelAttr();
                for (Map<String, Object> entry : items) {
                    if (entry.get("$ref") != null) {
                        String refName = entry.get("$ref").toString();
                        modelAttr.getProperties().addAll(definitinMap.getOrDefault(refName, new ModelAttr()).getProperties());
                    } else if (entry.get("properties") != null) {
                        Map<String, Object> modeProperties1 = (Map<String, Object>) entry.get("properties");
                        List<ModelAttr> modelAttrList = getModelAttrs(definitions, definitinMap, modelAttr, modeProperties1);
                        List<ModelAttr> attrList = modelAttr.getProperties();
                        for (ModelAttr newModel : modelAttrList) {
                            for (int j = 0; j < attrList.size(); j++) {
                                if (newModel.getName().equals(attrList.get(j).getName())) {
                                    attrList.set(j, newModel);
                                }
                            }
                        }
                    }
                }
                for (ModelAttr modelAttr1: modelAttr.getProperties()) {
                    responseMap.put(modelAttr1.getName(), getValue(modelAttr1.getType(), modelAttr1));
                }
                return JsonUtils.writePrettyJSON(responseMap);
            }
        }
        return StringUtils.EMPTY;
    }

    /**
     * 封装请求体
     *
     * @param list
     * @return
     */
    private String processRequestParam(List<Request> list) throws IOException {
        Map<String, Object> jsonMap = new LinkedHashMap<>();
        if (list != null && list.size() > 0) {
            for (Request request : list) {
                String name = request.getName();
                String paramType = request.getParamType();
                Object value = getValue(request.getType(), request.getModelAttr());
                switch (paramType) {
                    case "body":{
                        //TODO 根据content-type序列化成不同格式，目前只用了json
                        jsonMap.put(name, value);
                        break;
                    }
                    default:
                        break;
                }
            }
        }
        String res = "";
        if (!jsonMap.isEmpty()) {
            if (jsonMap.size() != 1) {
                for (Entry<String, Object> entry : jsonMap.entrySet()) {
                    res += JsonUtils.writePrettyJSON(entry.getValue());
                }
            } else {
                if (jsonMap.containsKey("body")) {
                    res += JsonUtils.writePrettyJSON(jsonMap.get("body"));
                } else {
                    res += JsonUtils.writePrettyJSON(jsonMap);
                }
            }
        }
        return res;
    }

    /**
     * 例子中，字段的默认值
     *
     * @param type      类型
     * @param modelAttr 引用的类型
     * @return
     */
    private Object getValue(String type, ModelAttr modelAttr) {
        int pos;
        if ((pos = type.indexOf(":")) != -1) {
            type = type.substring(0, pos);
        }
        switch (type) {
            case "string":
                return "string";
            case "string(date-time)":
                return "2020/01/01 00:00:00";
            case "integer":
            case "integer(int64)":
            case "integer(int32)":
                return 0;
            case "number":
                return 0;
            case "boolean":
                return true;
            case "file":
                return "(binary)";
            case "array":
                List list = new ArrayList();
                Map<String, Object> map = new LinkedHashMap<>();
                if (modelAttr != null && !CollectionUtils.isEmpty(modelAttr.getProperties())) {
                    for (ModelAttr subModelAttr : modelAttr.getProperties()) {
                        map.put(subModelAttr.getName(), getValue(subModelAttr.getType(), subModelAttr));
                    }
                    list.add(map);
                }
                return list;
            case "body":
            case "object":
                map = new LinkedHashMap<>();
                if (modelAttr != null && !CollectionUtils.isEmpty(modelAttr.getProperties())) {
                    for (ModelAttr subModelAttr : modelAttr.getProperties()) {
                        map.put(subModelAttr.getName(), getValue(subModelAttr.getType(), subModelAttr));
                    }
                }
                return map;
            default:
                return null;
        }
    }

    /**
     * 将map转换成url
     */
    public static String getUrlParamsByMap(Map<String, Object> map) {
        if (CollectionUtils.isEmpty(map)) {
            return "";
        }
        StringBuilder sBuilder = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sBuilder.append(entry.getKey() + "=" + entry.getValue());
            sBuilder.append("&");
        }
        String s = sBuilder.toString();
        if (s.endsWith("&")) {
            s = StringUtils.substringBeforeLast(s, "&");
        }
        return s;
    }

    /**
     * 将map转换成header
     */
    public static String getHeaderByMap(Map<String, Object> map) {
        if (CollectionUtils.isEmpty(map)) {
            return "";
        }
        StringBuilder sBuilder = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sBuilder.append("--header '");
            sBuilder.append(entry.getKey() + ":" + entry.getValue());
            sBuilder.append("'");
        }
        return sBuilder.toString();
    }
}
