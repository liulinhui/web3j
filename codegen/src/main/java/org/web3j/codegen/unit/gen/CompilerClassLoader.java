/*
 * Copyright 2019 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.web3j.codegen.unit.gen;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/** Class loader with compilation capabilities. */
public class CompilerClassLoader extends ClassLoader {

    private final File outputDir;
    private final URL[] urls;

    /**
     * Creates a class loader from the given source URLs.
     *
     * @param outputDir Directory where classes will be compiled.
     * @param urls Classpath URLs to compile the Java sources.
     */
    public CompilerClassLoader(final File outputDir, final URL... urls) {
        super(CompilerClassLoader.class.getClassLoader());
        this.outputDir = outputDir;
        this.urls = urls;
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
        List<Class<?>> loadedClasses =
                compileClass(name).stream()
                        .map(
                                classFile -> {
                                    Optional<byte[]> bytes = readBytes(classFile);
                                    return defineClass(
                                            extractClassName(classFile.toString()),
                                            bytes.get(),
                                            0,
                                            bytes.get().length);
                                })
                        .collect(Collectors.toList());
        return loadedClasses.stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new ClassNotFoundException(name));
    }

    private List<File> compileClass(final String name) {

        final String path = name.replace(".", File.separator);
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        File sourceFile = null;
        for (final URL url : urls) {
            File file;
            try {
                file = new File(URLDecoder.decode(url.getPath(), "UTF-8"), path + ".java");
            } catch (Exception e) {
                file = new File(url.getFile(), path + ".java");
            }

            if (file.exists()) {
                sourceFile = file;
                break;
            }
        }

        if (sourceFile == null) {
            // Try to find the Java file in newly generated files
            sourceFile = new File(outputDir, path + ".java");
        }

        if (!sourceFile.exists()) {
            return List.of();
        }

        final Iterable<? extends JavaFileObject> javaFileObjects =
                compiler.getStandardFileManager(null, null, null).getJavaFileObjects(sourceFile);

        final List<String> options =
                Arrays.asList(
                        "-d", outputDir.getAbsolutePath(),
                        "-cp", buildClassPath());

        final CompilationTask task =
                compiler.getTask(null, null, System.err::println, options, null, javaFileObjects);

        if (task.call()) {
            try {
                return Files.walk(Paths.get(outputDir.getAbsoluteFile().toURI()))
                        .filter(Files::isRegularFile)
                        .filter(
                                filePath -> {
                                    String fileName = extractClassName(filePath.toString());
                                    return fileName.startsWith(name);
                                })
                        .map(Path::toFile)
                        .toList();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to process compiled classes: ", e);
            }
        }
        return List.of();
    }

    private String extractClassName(final String pathName) {
        return pathName.substring(outputDir.toString().length() + 1, pathName.lastIndexOf("."))
                .replaceAll("[/\\\\]", ".");
    }

    private String buildClassPath() {
        return buildClassPath(urls)
                + getClassPathSeparator()
                + System.getProperty("java.class.path");
    }

    private String getClassPathSeparator() {
        if (isWindows()) {
            return ";";
        } else {
            return ":";
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().startsWith("windows");
    }

    private String buildClassPath(final URL... urls) {
        return Arrays.stream(urls)
                .map(URL::toExternalForm)
                .map(url -> url.replaceAll("file:", ""))
                .collect(Collectors.joining(getClassPathSeparator()));
    }

    private Optional<byte[]> readBytes(final File file) {
        try {
            return Optional.of(Files.readAllBytes(Paths.get(file.toURI())));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
