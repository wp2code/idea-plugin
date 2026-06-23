package com.lsy.idea.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.lsy.idea.db.DatabaseManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

/**
 * 插件启动 Activity：项目打开时初始化 SQLite 数据库
 * 注册在 plugin.xml 的 <postStartupActivity>
 * 使用 ProjectActivity 接口（IDEA 2023.1+）
 * 将阻塞 I/O 操作调度到后台线程池，避免在协程中阻塞 EDT
 */
public class PluginStartupActivity implements ProjectActivity {

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // 将阻塞式数据库初始化操作提交到后台线程池，避免阻塞协程/EDT
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            DatabaseManager dbManager = DatabaseManager.getInstance(project);
            if (dbManager != null) {
                dbManager.initDatabase();
            }
        });
        return Unit.INSTANCE;
    }
}
