package com.testflow.apitest.utilities;

import com.testflow.apitest.servicesaccess.ServiceAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

public class VerifyUtil {
    private static Logger logger = LoggerFactory.getLogger(VerifyUtil.class);
    private StringBuffer errorMsg = new StringBuffer();
    private Stack<String> index = new Stack<>();

    /**
     * 获取对比错误信息
     *
     * @return 返回 ErrorMsg
     */
    public String getErrorMsg() {
        return errorMsg.toString();
    }

    /**
     * 对比两个实体，实体中不含有List，并且不含无需对比item
     *
     * @param expObj 预期实体
     * @param atlObj 实际实体
     * @return 返回 ErrorMsg
     */
    public void compareEntity(Object expObj, Object atlObj)
    {
        Map<String, List<String>> pkMap = new HashMap<>();
        Map<String, List<String>> noCompareItemMap = new HashMap<>();
        compareEntity(expObj,atlObj, pkMap, noCompareItemMap);
    }

    /**
     * 对比两个实体，实体中含有List，主键只能为值类型
     *
     * @param expObj 预期实体
     * @param atlObj 实际实体
     * @param atlObj 实体中List的主键List
     * @return 返回 ErrorMsg
     */
    public void compareEntity(Object expObj, Object atlObj, Map<String, List<String>> pkMap, Map<String, List<String>> noCompareItemMap)
    {
        if (isList(expObj) && isList(atlObj))
        {
            List<Object> expObjList = new ArrayList<>();
            List<Object> atlObjList = new ArrayList<>();
            try {
                expObjList = (ArrayList) expObj;
                atlObjList = (ArrayList) atlObj;
            }
            catch (Exception ex)
            {
                errorMsg.append(String.format("Parse data \"%s\", \"%s\" to list failed.\n", expObj.toString(), atlObj.toString()) + ex);
            }
            compareList(expObjList, atlObjList, pkMap, noCompareItemMap);
        }
        else
        {
            //取出bean里的不对比的属性
            List<String> noCompareItemList = noCompareItemMap.get(expObj.getClass().getSimpleName());
            //取出bean里的所有方法和属性
            Field[] fields = ServiceAccess.reflectDeclaredFields(atlObj);
            for (Field f : fields) {
                //根据属性名判断，有关属性不进行匹配
                if (noCompareItemList !=null && noCompareItemList.contains(f.getName())){
                    continue;
                }
                Object cAtlObj = ServiceAccess.reflectField(atlObj, f);
                Object cExpObj = ServiceAccess.reflectField(expObj, f);
                if (null != cAtlObj && cAtlObj.getClass().getSimpleName().toLowerCase().equals("recordschema")) {
                    continue;
                }
                index.push(f.getName());
                if(!equals(cExpObj, cAtlObj, pkMap, noCompareItemMap)) {
                    errorMsg.append(String.format("Index: %s expected: \"%s\" not equals with actual: \"%s\".\n", index, cExpObj, cAtlObj));
                }
                else {
                    //logger.info(String.format("Index: %s expected: \"%s\" not equals with actual: \"%s\".", index, cExpObj, cAtlObj));
                }
                index.pop();
            }
        }

    }

    /**
     * 根据主键对比两个DB List
     *
     * @param expObjList 预期实体List
     * @param atlObjList 预期实体List
     * @param pkMap 主键键值对
     */
    private void compareList(List<Object> expObjList, List<Object> atlObjList, Map<String, List<String>> pkMap, Map<String, List<String>> noCompareItemMap)
    {
        //对比过的List实体
        List<Object> objListCompared = new ArrayList<>();
        //如果两个List长度不相等，返回error message
        if (expObjList.size() != atlObjList.size()) {
            errorMsg.append(String.format("List %s length not mathched. Expected: \"%d\", Actual \"%d\" Expected List: \"%s\", Actual List \"%s\".\n", index, expObjList.size(), atlObjList.size(), expObjList, atlObjList));
        }
        //循环遍历exp List
        for (Object expObjItem : expObjList) {
            int i = 0;
            int j = atlObjList.size();
            //循环遍历atl List
            List<String> pkList = pkMap.get(expObjItem.getClass().getSimpleName());
            List<String> noCompareItemList = noCompareItemMap.get(expObjItem.getClass().getSimpleName());
            for (Object atlObjItem : atlObjList) {
                List<Object> primaryFields = ServiceAccess.getPrimaryFields(expObjItem, pkList);
                if (primaryFields.size() > 0) {
                    //根据实体主键对比实体，如果不相等则continue
                    if (!compareObjWithSpecificCol(expObjItem, atlObjItem, pkList)) {
                        i++;
                        continue;
                    }
                    index.push(expObjItem.getClass().getSimpleName() + pkList + primaryFields);
                    //获取当前对比实体的所有属性
                    Field[] fs = ServiceAccess.reflectDeclaredFields(atlObjItem);
                    for (Field f : fs) {
                        //根据属性名判断，有关属性不进行匹配
                        if (noCompareItemList != null && noCompareItemList.contains(f.getName())) {
                            continue;
                        }
                        Object atlObj = ServiceAccess.reflectField(atlObjItem, f);
                        Object expObj = ServiceAccess.reflectField(expObjItem, f);
                        if (null != atlObj && atlObj.getClass().getSimpleName().toLowerCase().equals("recordschema")) {
                            continue;
                        }
                        index.push(f.getName());
                        if (!equals(expObj, atlObj, pkMap, noCompareItemMap)) {
                            errorMsg.append(String.format("Index: %s expected: \"%s\" not equals with actual: \"%s\".\n", index, expObj, atlObj));
                        } else {
                            //logger.info(String.format("Index: %s expected obj: \"%s\" equals with actual obj \"%s\".", index, expObj, atlObj));
                        }
                        index.pop();
                    }
                    index.pop();
                    //对比过的实体从list中删除
                    atlObjList.remove(atlObjItem);
                    break;

                } else {
                    //对比list<String> 类型
                    if ("String".equals(atlObjItem.getClass().getSimpleName()) || "Integer".equals(atlObjItem.getClass().getSimpleName())) {
                        if (!expObjList.contains(atlObjItem)) {
                            errorMsg.append(String.format("Entity List: \"%s\" value \"%s\" actual value not found.\n", expObjList, atlObjItem));
                        } else {
                            atlObjList.remove(atlObjItem);
                            break;
                        }
                    } else {
                        errorMsg.append(String.format("Current entity \"%s\" primary key: \"%s\" no found, please check if the primary keys are correct.\n", expObjItem, ServiceAccess.getPrimaryFieldsStrViaList(pkList)));
                    }
                }
            }
            //如果预期值在实际值的objList中不存在
            if (j == i) {
                errorMsg.append(String.format("Entity: \"%s\" with primary key: \"%s\" value \"%s\" actual value not found.\n", expObjItem, ServiceAccess.getPrimaryFieldsStrViaList(pkList), ServiceAccess.getPrimaryFields(expObjItem, pkList)));
            }
        }

        for(Object leftObj : atlObjList)
        {
            //获取主键List
            List<String> pkList = pkMap.get(leftObj.getClass().getSimpleName());
            errorMsg.append(String.format("Entity: \"%s\" with primary key: \"%s\" value \"%s\" expect value not found.\n", leftObj, ServiceAccess.getPrimaryFieldsStrViaList(pkList), ServiceAccess.getPrimaryFields(leftObj, pkList)));
        }
    }

    /**
     * 判断两个实体对应的属性List是否相等
     *
     * @param obj1 对比的第一个实体
     * @param obj2 对比的第二个实体
     * @param fieldPaths 需要对比的属性List
     * @return boolean 是否相等
     */
    public boolean compareObjWithSpecificCol(Object obj1, Object obj2, List<String> fieldPaths)
    {
        boolean ret = true;
        for (String field : fieldPaths)
        {
            if(!DataValEquals(ServiceAccess.reflectField(obj1, field), ServiceAccess.reflectField(obj2, field)))
            {
                ret = false;
            }
        }
        return ret;
    }

    /**
     * 判断实体是否为List
     *compareList
     * @param bean 反射类名
     * @return Class<?> 返回类Class
     */
    private boolean isList(Object bean)
    {
        boolean ret = false;
        if(bean != null && bean.getClass() != null && bean.getClass().getSimpleName().equals("ArrayList")) {
            ret = true;
        }
        return ret;
    }

    /**
     * 判断是否存在某属性的 get方法
     *
     * @param field : 需要获取方法对应的属性
     * @param obj ： 需要获取方法对应的实例
     * @return Method 返回get方法字节码
     */
    private Method getValueViaGetMet(Field field, Object obj) {
        String fieldSetName = parGetName(field.getName());
        Method[] methods = ServiceAccess.reflectDeclaredMethod(obj);
        Method fieldSetMet = getGetMet(methods, fieldSetName);
        if (fieldSetMet == null) {
            logger.warn(String.format("%s: Get field \"%s\" get method from object \"%s\" failed, please check if field name or method is exist.", new Date(), field, obj, field));
        }
        return fieldSetMet;
    }

    /**
     * 获取某属性的 get方法
     *
     * @param methods
     * @param fieldGetMet
     * @return boolean
     */
    private Method getGetMet(Method[] methods, String fieldGetMet) {
        Method tarMet = null;
        for (Method met : methods) {
            if (fieldGetMet.equals(met.getName())) {
                tarMet = met;
            }
        }
        if (tarMet == null)
        {
            //logger.info(String.format("Get method \"%s\" fieldGetMet failed, please check if method \"%s\" is exist.", fieldGetMet, fieldGetMet));
        }
        return tarMet;
    }

    /**
     * 拼接属性的 get方法
     *
     * @param fieldName
     * @return String
     */
    private String parGetName(String fieldName) {
        if (null == fieldName || "".equals(fieldName)) {
            return null;
        }
        int startIndex = 0;
        if (fieldName.charAt(0) == '_') {
            startIndex = 1;
        }
        return "get"
                + fieldName.substring(startIndex, startIndex + 1).toUpperCase()
                + fieldName.substring(startIndex + 1);
    }

    /**
     * 对比DB实体
     *equals
     * @param obj1 对比实体1
     * @param obj2 对比实体2
     * @return boolean 是否相等
     */
    private boolean equals(Object obj1, Object obj2, Map<String, List<String>> pkMap, Map<String, List<String>> noCompareItemMap) {
        logger.info(String.format("%s: Start to compare object %s, Expected: \"%s\", Actual \"%s\".", new Date(), index, obj1, obj2));
        if (obj1 == null && "".equals(obj2)) {
            return true;
        }
        else if ("".equals(obj1) && obj2 == null) {
            return true;
        }
        else if (obj1 == null && obj2 == null) {
            return true;
        }
        else if (obj1 == null && obj2 != null) {
            return false;
        }
        else if (obj1 != null && obj2 == null) {
            return false;
        }

        else if (obj1 instanceof Integer) {
            int value1 = (Integer) obj1;
            int value2 = (Integer) obj2;
            return value1 == value2;
        }
        else if (obj1 instanceof BigDecimal) {
            double value1 = ((BigDecimal) obj1).doubleValue();
            double value2 = ((BigDecimal) obj2).doubleValue();
            return value1 == value2;
        }
        else if (obj1 instanceof String) {
            String value1 = (String) obj1;
            String value2 = (String) obj2;
            return value1.equals(value2);
        }
        else if (obj1 instanceof Double) {
            double value1 = (Double) obj1;
            double value2 = (Double) obj2;
            return value1 == value2;
        }
        else if (obj1 instanceof Float) {
            float value1 = (Float) obj1;
            float value2 = (Float) obj2;
            return value1 == value2;
        } else if (obj1 instanceof Long) {
            long value1 = (Long) obj1;
            long value2 = (Long) obj2;
            return value1 == value2;
        }
        else if (obj1 instanceof Boolean) {
            boolean value1 = (Boolean) obj1;
            boolean value2 = (Boolean) obj2;
            return value1 == value2;
        }
        else if (obj1 instanceof Date) {
            Date value1 = (Date) obj1;
            Date value2 = (Date) obj1;
            return value1.toString().equals(value2.toString());
        }
        else if (obj1 != null && obj1.getClass() != null && obj1.getClass().getName() != null && obj1.getClass().getName().toLowerCase().contains("enum")) {
            String value1 = obj1.toString();
            String value2 = obj2.toString();
            return value1.equals(value2);
        }
        else {
            compareEntity(obj1, obj2, pkMap, noCompareItemMap);
            return true;
        }
    }

    public static boolean DataValEquals(Object obj1, Object obj2) {
        if (obj1 == null && "".equals(obj2)) {
            return true;
        }
        else if ("".equals(obj1) && obj2 == null) {
            return true;
        }
        else if (obj1 == null && obj2 == null) {
            return true;
        }
        else if (obj1 == null && obj2 != null) {
            return false;
        }
        else if (obj1 != null && obj2 == null) {
            return false;
        }
        else if (obj1 instanceof Integer) {
            int value1 = ((Integer) obj1).intValue();
            int value2 = ((Integer) obj2).intValue();
            return value1 == value2;
        }
        else if (obj1 instanceof String) {
            String value1 = (String) obj1;
            String value2 = (String) obj2;
            return value1.equals(value2);
        }
        else if (obj1 instanceof Double) {
            double value1 = ((Double) obj1).doubleValue();
            double value2 = ((Double) obj2).doubleValue();
            return value1 == value2;
        }
        else if (obj1 instanceof Float) {
            float value1 = ((Float) obj1).floatValue();
            float value2 = ((Float) obj2).floatValue();
            return value1 == value2;
        }
        else if (obj1 instanceof Long) {
            long value1 = ((Long) obj1).longValue();
            long value2 = ((Long) obj2).longValue();
            return value1 == value2;
        }
        else if (obj1 instanceof Boolean) {
            boolean value1 = ((Boolean) obj1).booleanValue();
            boolean value2 = ((Boolean) obj2).booleanValue();
            return value1 == value2;
        }
        else if (obj1 instanceof Date) {
            Date value1 = (Date) obj1;
            Date value2 = (Date) obj1;
            return value1.toString().equals(value2.toString());
        }
        else {
            return false;
        }
    }
}
