package com.crypto.trade;

import com.crypto.trade.entity.ScheduledTask;
import com.crypto.trade.entity.TaskExecution;
import com.crypto.trade.repository.ScheduledTaskRepository;
import com.crypto.trade.repository.TaskExecutionRepository;
import com.crypto.trade.service.task.TaskManagerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TaskIntegrationTest {

    @Autowired
    private TaskManagerService taskManagerService;

    @Autowired
    private ScheduledTaskRepository scheduledTaskRepository;

    @Autowired
    private TaskExecutionRepository taskExecutionRepository;

    @Test
    public void testCreateAndRetrieveTask() {
        // 创建测试任务
        ScheduledTask task = new ScheduledTask();
        task.setTaskName("测试数据获取任务");
        task.setTaskType("data_fetch");
        task.setCronExpression("0 */5 * * * ?");
        task.setTimeoutSeconds(300);
        task.setStatus("active");
        task.setParentTaskId(0L);
        task.setDescription("测试任务描述");

        // 保存任务
        ScheduledTask savedTask = scheduledTaskRepository.save(task);

        // 验证保存成功
        assertNotNull(savedTask.getTaskId());
        assertEquals("测试数据获取任务", savedTask.getTaskName());
        assertEquals("data_fetch", savedTask.getTaskType());

        // 获取所有任务
        List<ScheduledTask> allTasks = taskManagerService.getAllTasks();
        assertFalse(allTasks.isEmpty());

        // 通过ID获取
        ScheduledTask retrievedTask = scheduledTaskRepository.findById(savedTask.getTaskId()).orElse(null);
        assertNotNull(retrievedTask);
        assertEquals(savedTask.getTaskId(), retrievedTask.getTaskId());
    }

    @Test
    public void testTaskExecutionFlow() {
        // 创建测试任务
        ScheduledTask task = new ScheduledTask();
        task.setTaskName("测试执行任务");
        task.setTaskType("data_calculation");
        task.setCronExpression("0 */10 * * * ?");
        task.setTimeoutSeconds(600);
        task.setStatus("active");
        task.setParentTaskId(0L);
        task.setDescription("测试执行流程");

        ScheduledTask savedTask = scheduledTaskRepository.save(task);

        // 创建执行记录
        TaskExecution execution = new TaskExecution();
        execution.setTaskId(savedTask.getTaskId());
        // execution.setTaskName(savedTask.getTaskName());
        execution.setTriggerType("cron");
        execution.setExecutionStatus("success");
        execution.setTriggerTime(LocalDateTime.now());
        execution.setActualExecuteTime(LocalDateTime.now());
        execution.setFinishTime(LocalDateTime.now());
        execution.setParentTaskName("st");

        TaskExecution savedExecution = taskExecutionRepository.save(execution);

        // 验证执行记录
        assertNotNull(savedExecution.getExecutionId());
        assertEquals(savedTask.getTaskId(), savedExecution.getTaskId());
        assertEquals("success", savedExecution.getExecutionStatus());

        // 获取任务执行记录
        List<TaskExecution> taskExecutions = taskExecutionRepository.findByTaskId(savedTask.getTaskId());
        assertFalse(taskExecutions.isEmpty());
        assertEquals(savedTask.getTaskId(), taskExecutions.get(0).getTaskId());
    }

    @Test
    public void testGetTasksByType() {
        // 创建不同类型任务
        ScheduledTask dataFetchTask = new ScheduledTask();
        dataFetchTask.setTaskName("数据获取任务");
        dataFetchTask.setTaskType("data_fetch");
        dataFetchTask.setCronExpression("0 */5 * * * ?");
        dataFetchTask.setStatus("active");
        dataFetchTask.setParentTaskId(0L);
        scheduledTaskRepository.save(dataFetchTask);

        ScheduledTask dataCalcTask = new ScheduledTask();
        dataCalcTask.setTaskName("数据计算任务");
        dataCalcTask.setTaskType("data_calculation");
        dataCalcTask.setCronExpression("0 */15 * * * ?");
        dataCalcTask.setStatus("active");
        dataCalcTask.setParentTaskId(0L);
        scheduledTaskRepository.save(dataCalcTask);

        // 按类型获取任务
        List<ScheduledTask> dataFetchTasks = taskManagerService.getTasksByType("data_fetch");
        assertEquals(1, dataFetchTasks.size());
        assertEquals("data_fetch", dataFetchTasks.get(0).getTaskType());

        List<ScheduledTask> dataCalcTasks = taskManagerService.getTasksByType("data_calculation");
        assertEquals(1, dataCalcTasks.size());
        assertEquals("data_calculation", dataCalcTasks.get(0).getTaskType());
    }

    @Test
    public void testUpdateTaskStatus() {
        // 创建测试任务
        ScheduledTask task = new ScheduledTask();
        task.setTaskName("状态更新测试");
        task.setTaskType("data_fetch");
        task.setCronExpression("0 */5 * * * ?");
        task.setStatus("active");
        task.setParentTaskId(0L);

        ScheduledTask savedTask = scheduledTaskRepository.save(task);

        // 更新状态
        taskManagerService.updateTaskStatus(savedTask.getTaskId(), "inactive");

        // 验证状态更新
        ScheduledTask updatedTask = scheduledTaskRepository.findById(savedTask.getTaskId()).orElse(null);
        assertEquals("inactive", updatedTask.getStatus());
    }
}