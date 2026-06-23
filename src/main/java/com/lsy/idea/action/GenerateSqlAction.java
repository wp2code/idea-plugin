package com.lsy.idea.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.lsy.idea.model.InterfaceInfo;
import com.lsy.idea.ui.MainDialog;
import com.lsy.idea.util.SpringAnnotationUtils;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 右键 Controller 类或方法 → 自动判断：
 * - 如果光标在 Spring 映射方法上，解析单个方法生成单条权限 SQL
 * - 如果光标在 Controller 类上，解析整个类批量生成权限 SQL
 */
public class GenerateSqlAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        List<InterfaceInfo> interfaces;

        // 优先判断是否在方法上
        PsiMethod psiMethod = getPsiMethod(e);
        if (psiMethod != null && hasMappingAnnotation(psiMethod)) {
            interfaces = SpringAnnotationUtils.extractMethodRoutes(psiMethod);
            if (interfaces.isEmpty()) {
                Messages.showInfoMessage(project, "当前方法未解析到有效接口映射", "提示");
                return;
            }
        } else {
            // 否则尝试解析 Controller 类
            PsiClass psiClass = getPsiClass(e);
            if (psiClass == null) return;
            interfaces = SpringAnnotationUtils.extractRoutes(psiClass);
            if (interfaces.isEmpty()) {
                Messages.showInfoMessage(project, "当前选中类未解析到有效接口", "提示");
                return;
            }
        }

        MainDialog dialog = new MainDialog(project, interfaces);
        dialog.show();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 在 Spring 方法上显示，或在 Controller 类上显示
        PsiMethod psiMethod = getPsiMethod(e);
        if (psiMethod != null && hasMappingAnnotation(psiMethod)) {
            e.getPresentation().setEnabledAndVisible(true);
            return;
        }
        PsiClass psiClass = getPsiClass(e);
        e.getPresentation().setEnabledAndVisible(
                psiClass != null && SpringAnnotationUtils.isController(psiClass));
    }

    @Nullable
    private PsiMethod getPsiMethod(@NotNull AnActionEvent e) {
        PsiElement psiElement = e.getData(LangDataKeys.PSI_ELEMENT);
        if (psiElement instanceof PsiMethod psiMethod) {
            return psiMethod;
        }
        return null;
    }

    @Nullable
    private PsiClass getPsiClass(@NotNull AnActionEvent e) {
        PsiElement psiElement = e.getData(LangDataKeys.PSI_ELEMENT);
        if (psiElement instanceof PsiClass psiClass) {
            return psiClass;
        }
        PsiJavaFile psiFile = (PsiJavaFile) e.getData(LangDataKeys.PSI_FILE);
        if (psiFile != null) {
            for (PsiClass cls : psiFile.getClasses()) {
                if (SpringAnnotationUtils.isController(cls)) return cls;
            }
            if (psiFile.getClasses().length > 0) return psiFile.getClasses()[0];
        }
        return null;
    }

    /**
     * 检查方法是否有 Spring 映射注解
     */
    private boolean hasMappingAnnotation(@NotNull PsiMethod method) {
        String[] annotations = {
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping"
        };
        for (String ann : annotations) {
            if (method.getAnnotation(ann) != null) return true;
        }
        return false;
    }
}
