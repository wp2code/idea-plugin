package com.lsy.idea.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.lsy.idea.model.InterfaceInfo;
import com.lsy.idea.model.MethodTypeEnum;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Spring Controller 注解解析工具（重写版） 支持： - @RequestMapping / @GetMapping / @PostMapping / @PutMapping / @DeleteMapping / @PatchMapping -
 *
 * @ApiOperation(value="") / @Operation(summary="") 提取接口名称 - Javadoc 第一行文字提取接口名称 - @I18nCode 注解提取国际化标识
 */
public class SpringAnnotationUtils {
    
    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
    
    private static final String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
    
    private static final String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    
    private static final String PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
    
    private static final String DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping";
    
    private static final String PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping";
    
    private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    
    private static final String CONTROLLER = "org.springframework.stereotype.Controller";
    
    /**
     * 方法级别映射注解 -> 默认 HTTP 方法
     */
    private static final Map<String, String> MAPPING_ANNOTATIONS = Map.of(GET_MAPPING, MethodTypeEnum.GET.name(), POST_MAPPING,
            MethodTypeEnum.POST.name(), PUT_MAPPING, MethodTypeEnum.PUT.name(), DELETE_MAPPING, MethodTypeEnum.DELETE.name(), PATCH_MAPPING,
            MethodTypeEnum.PATCH.name());
    
    // ===================== 公开 API =====================
    
    /**
     * 从 PsiClass 中提取所有接口信息（自动读取 context-path 前缀）
     */
    @NotNull
    public static List<InterfaceInfo> extractRoutes(@NotNull PsiClass psiClass) {
        List<InterfaceInfo> list = new ArrayList<>();
        if (!isController(psiClass)) {
            return list;
        }
        
        String classLevelPath = getClassLevelPath(psiClass);
        String className = psiClass.getName() != null ? psiClass.getName() : "Unknown";
        VirtualFile sourceFile = getVirtualFile(psiClass);
        String contextPath = readContextPath(psiClass.getProject(), sourceFile);
        for (PsiMethod method : psiClass.getMethods()) {
            List<InterfaceInfo> routes = extractMethodRoutes(method, className, classLevelPath);
            if (!contextPath.isEmpty()) {
                for (InterfaceInfo info : routes) {
                    info.setRoutePath(InterfaceInfo.mergePath(contextPath, info.getRoutePath()));
                }
            }
            list.addAll(routes);
        }
        return list;
    }
    
    /**
     * 从 PsiMethod 中提取接口信息（自动推断类路径，自动读取 context-path 前缀）
     */
    @NotNull
    public static List<InterfaceInfo> extractMethodRoutes(@NotNull PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return Collections.emptyList();
        }
        String className = containingClass.getName() != null ? containingClass.getName() : "Unknown";
        String classLevelPath = getClassLevelPath(containingClass);
        VirtualFile sourceFile = getVirtualFile(containingClass);
        String contextPath = readContextPath(containingClass.getProject(), sourceFile);
        List<InterfaceInfo> routes = extractMethodRoutes(method, className, classLevelPath);
        if (!contextPath.isEmpty()) {
            for (InterfaceInfo info : routes) {
                info.setRoutePath(InterfaceInfo.mergePath(contextPath, info.getRoutePath()));
            }
        }
        return routes;
    }
    
    /**
     * 判断是否为 Controller 类
     */
    public static boolean isController(@NotNull PsiClass psiClass) {
        return psiClass.getAnnotation(REST_CONTROLLER) != null || psiClass.getAnnotation(CONTROLLER) != null;
    }
    
    /**
     * 获取类级别路径
     */
    @NotNull
    public static String getClassLevelPath(@NotNull PsiClass psiClass) {
        PsiAnnotation classMapping = psiClass.getAnnotation(REQUEST_MAPPING);
        if (classMapping != null) {
            return getAnnotationValue(classMapping, "path", "value");
        }
        return "";
    }
    
    // ===================== 内部实现 =====================
    
    @NotNull
    private static List<InterfaceInfo> extractMethodRoutes(@NotNull PsiMethod method, @NotNull String className, @NotNull String classLevelPath) {
        
        List<InterfaceInfo> routes = new ArrayList<>();
        String interfaceName = extractInterfaceName(method);
        String i18nCode = extractI18nCode(method);
        
        // @RequestMapping
        PsiAnnotation requestMapping = method.getAnnotation(REQUEST_MAPPING);
        if (requestMapping != null) {
            String[] methods = getAnnotationValues(requestMapping, "method");
            String path = getAnnotationValue(requestMapping, "path", "value");
            String fullPath = InterfaceInfo.mergePath(classLevelPath, path);
            if (methods.length == 0) {
                routes.add(new InterfaceInfo("ALL", fullPath, interfaceName, i18nCode));
            } else {
                for (String m : methods) {
                    String httpMethod = m.replace("RequestMethod.", "").replaceAll(".*\\.", "").toUpperCase();
                    routes.add(new InterfaceInfo(httpMethod, fullPath, interfaceName, i18nCode));
                }
            }
        }
        
        // @GetMapping / @PostMapping 等
        for (Map.Entry<String, String> entry : MAPPING_ANNOTATIONS.entrySet()) {
            PsiAnnotation mapping = method.getAnnotation(entry.getKey());
            if (mapping != null) {
                String path = getAnnotationValue(mapping, "path", "value");
                String fullPath = InterfaceInfo.mergePath(classLevelPath, path);
                routes.add(new InterfaceInfo(entry.getValue(), fullPath, interfaceName, i18nCode));
            }
        }
        
        return routes;
    }
    
    /**
     * 提取接口名称 优先级：@ApiOperation(value) / @Operation(summary) > Javadoc 第一行 > ""
     */
    @NotNull
    private static String extractInterfaceName(@NotNull PsiMethod method) {
        // Swagger 2 @ApiOperation
        PsiAnnotation apiOp = method.getAnnotation("io.swagger.annotations.ApiOperation");
        if (apiOp != null) {
            String val = getAnnotationValue(apiOp, "value");
            if (!val.isEmpty()) {
                return val;
            }
        }
        // Swagger 3 / OpenAPI @Operation
        PsiAnnotation operation = method.getAnnotation("io.swagger.v3.oas.annotations.Operation");
        if (operation != null) {
            String val = getAnnotationValue(operation, "summary");
            if (!val.isEmpty()) {
                return val;
            }
        }
        // Javadoc 第一行
        String javadoc = extractJavadocFirstLine(method);
        if (!javadoc.isEmpty()) {
            return javadoc;
        }
        return "";
    }
    
    /**
     * 提取 @I18nCode 注解的 value，模糊匹配注解简名
     */
    @NotNull
    private static String extractI18nCode(@NotNull PsiMethod method) {
        for (PsiAnnotation annotation : method.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (qualifiedName != null && (qualifiedName.endsWith("I18nCode") || qualifiedName.endsWith("I18nResourceCode"))) {
                String val = getAnnotationValue(annotation, "value");
                if (!val.isEmpty()) {
                    return val;
                }
            }
        }
        return "";
    }
    
    /**
     * 提取方法 Javadoc 的第一行描述文字（去除 * 前缀和空白）
     */
    @NotNull
    private static String extractJavadocFirstLine(@NotNull PsiMethod method) {
        PsiDocComment docComment = method.getDocComment();
        if (docComment == null) {
            return "";
        }
        
        StringBuilder desc = new StringBuilder();
        for (PsiElement el : docComment.getDescriptionElements()) {
            if (el instanceof PsiDocToken token) {
                String tokenType = token.getTokenType().toString();
                if ("DOC_COMMENT_DATA".equals(tokenType) || tokenType.contains("COMMENT_DATA")) {
                    String text = token.getText().trim();
                    if (!text.isEmpty() && !text.equals("*")) {
                        // 清理行首 * 和空白
                        text = text.replaceAll("^\\*\\s*", "").trim();
                        if (!text.isEmpty()) {
                            desc.append(text);
                        }
                    }
                }
            } else if (el instanceof PsiWhiteSpace) {
                // 如果 desc 已有内容且遇到换行，停止
                if (!desc.isEmpty() && el.getText().contains("\n")) {
                    break;
                }
            }
        }
        
        // 如果上面没提取到内容，降级尝试文本解析
        if (desc.isEmpty()) {
            String rawText = docComment.getText();
            // 去除 /** 和 */，按行分割，取第一个非空行
            String[] lines = rawText.replaceAll("/\\*+", "").replaceAll("\\*/", "").split("\n");
            for (String line : lines) {
                String cleaned = line.replaceAll("^\\s*\\*\\s?", "").trim();
                if (!cleaned.isEmpty() && !cleaned.startsWith("@")) {
                    return cleaned;
                }
            }
            return "";
        }
        
        String result = desc.toString().trim();
        // 取第一行
        int newlineIdx = result.indexOf('\n');
        return newlineIdx > 0 ? result.substring(0, newlineIdx).trim() : result;
    }
    
    /**
     * 获取注解属性值（支持 path/value 两个别名，返回第一个非空的）
     */
    @NotNull
    public static String getAnnotationValue(@NotNull PsiAnnotation annotation, @NotNull String... attrNames) {
        for (String attr : attrNames) {
            PsiAnnotationMemberValue value = annotation.findAttributeValue(attr);
            if (value != null) {
                String resolved = resolveAnnotationValue(value);
                if (!resolved.isEmpty()) {
                    return resolved;
                }
            }
        }
        return "";
    }
    
    /**
     * 获取注解属性的多值（如 method = {RequestMethod.GET, RequestMethod.POST}）
     */
    @NotNull
    private static String[] getAnnotationValues(@NotNull PsiAnnotation annotation, @NotNull String attr) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attr);
        if (value == null) {
            return new String[0];
        }
        
        if (value instanceof PsiArrayInitializerMemberValue arrayValue) {
            PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
            String[] result = new String[initializers.length];
            for (int i = 0; i < initializers.length; i++) {
                result[i] = resolveAnnotationValue(initializers[i]);
            }
            return result;
        }
        
        String resolved = resolveAnnotationValue(value);
        return resolved.isEmpty() ? new String[0] : new String[] {resolved};
    }
    
    /**
     * 解析注解属性值（处理字面量、常量字段引用、字符串拼接、数组等）
     */
    @NotNull
    private static String resolveAnnotationValue(@NotNull PsiAnnotationMemberValue value) {
        return resolveAnnotationValue(value, 0);
    }
    
    /**
     * 解析注解属性值，限制递归深度防死循环
     */
    @NotNull
    private static String resolveAnnotationValue(@NotNull PsiAnnotationMemberValue value, int depth) {
        if (depth > 8) {
            return ""; // 防止无限递归
        }
        
        // 字符串/数值字面量
        if (value instanceof PsiLiteralExpression literal) {
            Object val = literal.getValue();
            return val != null ? val.toString() : "";
        }
        // 数组值：取第一个有效元素
        if (value instanceof PsiArrayInitializerMemberValue arrayValue) {
            for (PsiAnnotationMemberValue elem : arrayValue.getInitializers()) {
                String resolved = resolveAnnotationValue(elem, depth + 1);
                if (!resolved.isEmpty()) {
                    return resolved;
                }
            }
            return "";
        }
        // 字符串拼接：如 Const.PREFIX + "/user"
        if (value instanceof PsiBinaryExpression binary) {
            String left = "";
            String right = "";
            PsiExpression leftOp = binary.getLOperand();
            PsiExpression rightOp = binary.getROperand();
            left = resolveAnnotationValue(leftOp, depth + 1);
            if (rightOp != null) {
                right = resolveAnnotationValue(rightOp, depth + 1);
            }
            // 仅当两边均可解析时才拼接，否则返回可用的一边
            if (!left.isEmpty() && !right.isEmpty()) {
                return left + right;
            }
            if (!left.isEmpty()) {
                return left;
            }
            if (!right.isEmpty()) {
                return right;
            }
            return "";
        }
        // 常量字段引用（如 Const.BASE_URL 或 BASE_URL）
        if (value instanceof PsiReferenceExpression ref) {
            PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiField field) {
                PsiExpression initializer = field.getInitializer();
                if (initializer != null) {
                    // 初始化表达式可能是字面量、拼接或其他引用—尝试递归解析
                    String fieldVal = resolveAnnotationValue(initializer, depth + 1);
                    if (!fieldVal.isEmpty()) {
                        return fieldVal;
                    }
                    // 直接字面量
                    if (initializer instanceof PsiLiteralExpression litInit) {
                        Object litVal = litInit.getValue();
                        if (litVal != null) {
                            return litVal.toString();
                        }
                    }
                    // 字段初始化器也是拼接表达式
                    if (initializer instanceof PsiBinaryExpression binaryInit) {
                        String binResult = resolveBinaryExpression(binaryInit, depth + 1);
                        if (!binResult.isEmpty()) {
                            return binResult;
                        }
                    }
                }
            }
            // 无法深度解析时退化为字段名（保留可读标识）
            return ref.getReferenceName() != null ? ref.getReferenceName() : "";
        }
        // 前缀表达式（罕见）
        if (value instanceof PsiPrefixExpression prefix) {
            PsiExpression operand = prefix.getOperand();
            if (operand != null) {
                return prefix.getOperationTokenType() + resolveAnnotationValue(operand, depth + 1);
            }
        }
        // 兜底：清理首尾引号
        return value.getText().replaceAll("^\"|\"$", "").trim();
    }
    
    /**
     * 递归解析二元字符串拼接表达式（字段初始化器场景）
     */
    @NotNull
    private static String resolveBinaryExpression(@NotNull PsiBinaryExpression binary, int depth) {
        if (depth > 8) {
            return "";
        }
        String left = "";
        String right = "";
        PsiExpression leftOp = binary.getLOperand();
        PsiExpression rightOp = binary.getROperand();
        
        if (leftOp instanceof PsiLiteralExpression litL) {
            Object v = litL.getValue();
            left = v != null ? v.toString() : "";
        } else if (leftOp instanceof PsiReferenceExpression refL) {
            PsiElement resolved = refL.resolve();
            if (resolved instanceof PsiField f && f.getInitializer() instanceof PsiLiteralExpression litF) {
                Object v = litF.getValue();
                left = v != null ? v.toString() : "";
            } else {
                left = refL.getReferenceName() != null ? refL.getReferenceName() : "";
            }
        } else if (leftOp instanceof PsiBinaryExpression binaryL) {
            left = resolveBinaryExpression(binaryL, depth + 1);
        }
        
        if (rightOp instanceof PsiLiteralExpression litR) {
            Object v = litR.getValue();
            right = v != null ? v.toString() : "";
        } else if (rightOp instanceof PsiReferenceExpression refR) {
            PsiElement resolved = refR.resolve();
            if (resolved instanceof PsiField f && f.getInitializer() instanceof PsiLiteralExpression litF) {
                Object v = litF.getValue();
                right = v != null ? v.toString() : "";
            } else {
                right = refR.getReferenceName() != null ? refR.getReferenceName() : "";
            }
        } else if (rightOp instanceof PsiBinaryExpression binaryR) {
            right = resolveBinaryExpression(binaryR, depth + 1);
        }
        
        if (!left.isEmpty() && !right.isEmpty()) {
            return left + right;
        }
        if (!left.isEmpty()) {
            return left;
        }
        return right;
    }
    
    // ===================== context-path 读取 =====================
    
    /**
     * 获取 PsiClass 对应的 VirtualFile（源文件）
     */
    @Nullable
    private static VirtualFile getVirtualFile(@NotNull PsiClass psiClass) {
        PsiFile psiFile = psiClass.getContainingFile();
        return psiFile != null ? psiFile.getVirtualFile() : null;
    }
    
    /**
     * 读取目标项目的 server.servlet.context-path 配置值。 优先从 sourceFile 所在目录向上查找包含 src/main/resources 的模块根； 找不到时回退到项目根目录查找。 找不到或为空时返回空字符串。
     */
    @NotNull
    public static String readContextPath(@Nullable Project project) {
        return readContextPath(project, null);
    }
    
    /**
     * 读取目标项目的 server.servlet.context-path 配置值。 优先从 sourceFile 所在目录向上查找包含 src/main/resources 的模块根； 找不到时回退到项目根目录查找。 找不到或为空时返回空字符串。
     */
    @NotNull
    public static String readContextPath(@Nullable Project project, @Nullable VirtualFile sourceFile) {
        if (project == null) {
            return "";
        }
        
        // 1. 优先：从 sourceFile 所在目录向上查找模块资源目录
        if (sourceFile != null) {
            String val = searchContextPathFromSourceFile(sourceFile);
            if (val != null) {
                return val;
            }
        }
        
        // 2. 回退：从项目根目录按固定路径查找
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) {
            return "";
        }
        String val = searchContextPathInDir(baseDir);
        return val != null ? val : "";
    }
    
    /**
     * 从 sourceFile 所在目录向上遍历，寻找最近的 src/main/resources 或 src/main/resources/config 目录， 并从中读取 context-path 配置。
     */
    @Nullable
    private static String searchContextPathFromSourceFile(@NotNull VirtualFile sourceFile) {
        VirtualFile dir = sourceFile.isDirectory() ? sourceFile : sourceFile.getParent();
        while (dir != null) {
            // 检查当前目录下是否存在 src/main/resources
            String val = searchContextPathInDir(dir);
            if (val != null) {
                return val;
            }
            dir = dir.getParent();
        }
        return null;
    }
    
    /**
     * 在指定基础目录下查找 src/main/resources 和 src/main/resources/config， 读取 context-path 配置值。
     */
    @Nullable
    private static String searchContextPathInDir(@NotNull VirtualFile baseDir) {
        // 常见资源目录列表
        String[] resourceDirs = {"src/main/resources", "src/main/resources/config"};
        for (String dir : resourceDirs) {
            VirtualFile resDir = baseDir.findFileByRelativePath(dir);
            if (resDir == null) {
                continue;
            }
            // 优先 .properties
            String[] properties = new String[] {"bootstrap.properties", "application.properties"};
            for (final String property : properties) {
                VirtualFile propsFile = resDir.findChild(property);
                if (propsFile != null) {
                    String val = readContextPathFromProperties(propsFile);
                    if (val != null) {
                        return val;
                    }
                }
            }
            // 其次 .yml / .yaml
            String[] ymls = new String[] {"bootstrap.yaml", "bootstrap.yml", "application.yml", "application.yaml"};
            for (String ymlName : ymls) {
                VirtualFile ymlFile = resDir.findChild(ymlName);
                if (ymlFile != null) {
                    String val = readContextPathFromYaml(ymlFile);
                    if (val != null) {
                        return val;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 从 .properties 文件中提取 server.servlet.context-path
     */
    @Nullable
    private static String readContextPathFromProperties(@NotNull VirtualFile file) {
        try (InputStream is = file.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("server.servlet.context-path")) {
                    int idx = line.indexOf('=');
                    if (idx >= 0) {
                        return line.substring(idx + 1).trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    /**
     * 从 .yml/.yaml 文件中简单提取 server.servlet.context-path 支持格式： server: servlet: context-path: /api
     */
    @Nullable
    private static String readContextPathFromYaml(@NotNull VirtualFile file) {
        try (InputStream is = file.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            boolean inServer = false;
            boolean inServlet = false;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                
                int indent = getIndent(line);
                if (indent == 0) {
                    inServer = trimmed.startsWith("server:") || trimmed.equals("server");
                    inServlet = false;
                    continue;
                }
                if (!inServer) {
                    continue;
                }
                
                if (indent == 2) {
                    inServlet = trimmed.startsWith("servlet:") || trimmed.equals("servlet");
                    continue;
                }
                if (!inServlet) {
                    continue;
                }
                
                if (indent == 4 && trimmed.startsWith("context-path:")) {
                    String val = trimmed.substring("context-path:".length()).trim();
                    // 去除可能的引号
                    val = val.replaceAll("^['\"]|['\"]$", "").trim();
                    if (!val.isEmpty()) {
                        return val;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    /**
     * 计算行首空格数（缩进级别）
     */
    private static int getIndent(@NotNull String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                count++;
            } else if (c == '\t') {
                count += 2;
            } else {
                break;
            }
        }
        return count;
    }
}
