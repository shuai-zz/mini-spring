package org.example.io;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;
import java.util.function.Function;

/**
 * 保存所有配置项，对外提供查询功能
 * 支持三种查询方式：
 * 1. 按配置的key查询，如：getProperty("app.title")
 * 2. 以${abc.xyz}形式查询，如：getProperty("${app.title}")，常用于@Value("${app.title}")注入
 * 3. 带默认值的，以${abc.xyz:defaultValue}形式的查询，如：getProperty("${app.title:defaultValue}")，常用于@Value("${app.title:Summer}")注入
 */
public class PropertyResolver {
    Logger logger= LoggerFactory.getLogger(getClass());

    Map<String, String> properties = new HashMap<>();
    // 存储Class->Function
    Map<Class<?>, Function<String, Object>> converters=new HashMap<>();

    public PropertyResolver(Properties properties) {
        // 存入环境变量
        this.properties.putAll(System.getenv());
        // 存入Properties
        Set<String> names = properties.stringPropertyNames();
        for (String name : names) {
            this.properties.put(name, properties.getProperty(name));
        }
        if(logger.isDebugEnabled()){
            List<String> keys=new ArrayList<>(this.properties.keySet());
            Collections.sort(keys);
            for(String key : keys){
                logger.debug("PropertyResolver:{}={}",key,this.properties.get(key));
            }
        }

        // 将不同类型放入converters
        converters.put(String.class, s -> s);

        converters.put(byte.class, Byte::parseByte);
        converters.put(Byte.class, Byte::valueOf);

        converters.put(short.class, Short::parseShort);
        converters.put(Short.class, Short::valueOf);

        converters.put(int.class, Integer::parseInt);
        converters.put(Integer.class, Integer::valueOf);

        converters.put(long.class, Long::parseLong);
        converters.put(Long.class, Long::valueOf);

        converters.put(float.class, Float::parseFloat);
        converters.put(Float.class, Float::valueOf);

        converters.put(double.class, Double::parseDouble);
        converters.put(Double.class, Double::valueOf);
        converters.put(boolean.class, Boolean::parseBoolean);
        converters.put(Boolean.class, Boolean::valueOf);

        converters.put(LocalDate.class, LocalDate::parse);
        converters.put(LocalTime.class, LocalTime::parse);
        converters.put(LocalDateTime.class, LocalDateTime::parse);
        converters.put(ZonedDateTime.class, ZonedDateTime::parse);
        converters.put(Duration.class, Duration::parse);
        converters.put(ZoneId.class, ZoneId::of);
    }

    public boolean containsProperty(String key){
        return this.properties.containsKey(key);
    }

    /**
     * 按key查询
     * @param key 查询的key
     * @return 查询结果value
     */
    @Nullable
    public String getProperty(String key) {
        //解析${abc.xyz:defaultValue}
        PropertyExpr keyExpr = parsePropertyExpr(key);
        // 长得像${abc.xyz:defaultValue}的
        if (keyExpr != null) {
            if (keyExpr.defaultValue() != null) {
                //带默认值
                return getProperty(keyExpr.key(), keyExpr.defaultValue());
            } else {
                //不带默认值
                return getRequiredProperty(keyExpr.key());
            }
        }
        //普通key查询
        String value = this.properties.get(key);
        if (value != null) {
            return parseValue(value);
        }
        return value;
    }

    /**
     * getProperty中传入的参数，如：${APP_NAME:Summer}
     * @param key 这里的key：APP_NAME
     * @param defaultValue 这里的defaultValue：Summer
     * @return 获取到的value或默认value：Summer
     */
    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : parseValue(defaultValue);
    }


    /**
     * 类型转换，如boolean, int, Long等，以及Date, Duration等类型的注入
     * @param key getProperty中参数${APP_NAME}->这里的key：APP_NAME
     * @param targetType 目标类型
     * @return 转换后的对象
     * @param <T> 目标类型
     */
    @Nullable
    public <T> T getProperty(String key, Class<T> targetType){
        String value=getProperty(key);
        if(value==null) return null;
        return convert(targetType,value);
    }

    /**
     * 转换到指定Class类型
     * @param clazz
     * @param value
     * @return
     * @param <T>
     */
    //TODO
    @SuppressWarnings("unchecked")
    <T> T convert(Class<?> clazz, String value){
        Function<String, Object> fn=this.converters.get(clazz);
        if(fn==null){
            throw new IllegalArgumentException("Unsupported value type: " + clazz.getName());
        }
        return (T) fn.apply(value);
    }

    public <T> T getProperty(String key, Class<T> targetType, T defaultValue){
        String value=getProperty(key);
        if(value==null) return defaultValue;
        return convert(targetType,value);
    }


    /**
     * 递归解决嵌套的key，如${app.title:${APP_NAME:Summer}}
     * @param value ${APP_NAME:Summer}
     * @return 给定value或默认value：Summer
     */
    private String parseValue(String value) {
        // 获取value对应的PropertyExpr对象
        PropertyExpr expr = parsePropertyExpr(value);
        // 若expr为空（即不是嵌套的key）
        if (expr == null) {
            return value;
        }
        if (expr.defaultValue() != null) {
            return getProperty(expr.key(), expr.defaultValue());
        } else {
            return getRequiredProperty(expr.key());
        }
    }

    /**
     * 获取key对应的value，若key对应的value不存在，则抛出异常
     * @param key getProperty中参数${APP_NAME}->这里的key：APP_NAME
     * @return 对应value
     */
    private String getRequiredProperty(String key) {
        /*
        NOTE:
            getProperty有三个作用，处理"abc"/${abc}/${abc:defaultValue}
            这里重复调用getProperty()， 是将key再重新传入getProperty()，使用其中处理"abc"功能
         */
        String value = getProperty(key);
        return Objects.requireNonNull(value, "Property '" + key + "' not found.");
    }

    public <T> T getRequiredProperty(String key, Class<T> targetType){
        T value=getProperty(key,targetType);
        return Objects.requireNonNull(value, "Property '" + key + "' not found.");
    }


    PropertyExpr parsePropertyExpr(String key) {
        if (key.startsWith("${") && key.endsWith("}")) {
            //是否存在defaultValue
            int n = key.indexOf(":");
            if (n == (-1)) {
                //没有defaultValue
                String k = key.substring(2, key.length() - 1);
                return new PropertyExpr(k, null);
            } else {
                //有defaultValue
                String k = key.substring(2, n);
                String v = key.substring(n + 1, key.length() - 1);
                return new PropertyExpr(k, v);
            }
        }
        return null;
    }


    String notEmpty(String key){
        if(key.isEmpty()){
            throw new IllegalArgumentException("Invalid key: "+key);
        }
        return key;
    }
    record PropertyExpr(String key, String defaultValue) {
    }
}
