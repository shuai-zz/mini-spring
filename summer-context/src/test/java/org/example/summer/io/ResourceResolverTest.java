package org.example.summer.io;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;

import jakarta.annotation.sub.AnnoScan;
import org.example.io.ResourceResolver;
import org.junit.jupiter.api.Test;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.sql.DataSourceDefinition;

public class ResourceResolverTest {

    @Test
    public void scanClass() {
        /*
        NOTE:
         Thread.currentThread().getContextClassLoader() 获取当前线程的类加载器类加载器使用 getResources(path) 方法查找资源，在 Maven 项目中，main 类路径（src/main/java 编译后的字节码）和 test 类路径（src/test/java 编译后的字节码）是分开的
         */
        // main/org/example/io
        var pkg = "org.example.io";
        var rr = new ResourceResolver(pkg);
        List<String> classes = rr.scan(res -> {
            String name = res.name();
            if (name.endsWith(".class")) {
                return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
            }
            return null;
        });
        Collections.sort(classes);
        System.out.println(classes);
        String[] listClasses=new String[]{
                //list of some scan classes:
                "org.example.io.Resource",
                "org.example.io.ResourceResolver"
        };
        for(String listClass : listClasses){
            assertTrue(classes.contains(listClass));
        }

    }

    @Test
    public void scanJar() {
        // 获取PostConstruct注解的包名(jakarta.annotation)
        var pkg = PostConstruct.class.getPackageName();
        var rr = new ResourceResolver(pkg);
        List<String> classes = rr.scan(res -> {
            String name = res.name();
            if (name.endsWith(".class")) {
                return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
            }
            return null;
        });
        // classes in jar:
        assertTrue(classes.contains(PostConstruct.class.getName()));
        assertTrue(classes.contains(PreDestroy.class.getName()));
        assertTrue(classes.contains(PermitAll.class.getName()));
        assertTrue(classes.contains(DataSourceDefinition.class.getName()));
        // jakarta.annotation.sub.AnnoScan is defined in classes: （自定义注解AnnoScan）
        assertTrue(classes.contains(AnnoScan.class.getName()));
    }

    @Test
    public void scanTxt() {
        /* NOTE:
            为什么扫描txt文件必须位于resource目录下?
            Maven 编译时，默认只将 .java 文件编译到类路径中，
            getContextClassLoader只能访问类路径中的资源，非 Java 文件（如配置文件、txt 文件等）应该放在 src/main/resources 目录下，所有文件都会被复制到类路径根目录下
         */

        // main/org/example/scan
        var pkg = "org.example.scan";
        var rr = new ResourceResolver(pkg);
        List<String> classes = rr.scan(res -> {
            String name = res.name();
            if (name.endsWith(".txt")) {
                return name.replace("\\", "/");
            }
            return null;
        });
        Collections.sort(classes);
        assertArrayEquals(new String[] {
                // txt files:
                "org/example/scan/sub1/sub1.txt", //
                "org/example/scan/sub1/sub2/sub2.txt", //
                "org/example/scan/sub1/sub2/sub3/sub3.txt", //
        }, classes.toArray(String[]::new));
    }
}
