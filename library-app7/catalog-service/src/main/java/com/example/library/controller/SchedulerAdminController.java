package com.example.library.controller;

import com.example.library.entity.CatalogReport;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.repository.CatalogReportRepository;
import lombok.RequiredArgsConstructor;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ЛР6 — перегляд розкладу і ручний запуск завдань для демонстрації. Шлях /api/admin/** — лише ADMIN (SecurityFilterChain).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class SchedulerAdminController {

    private final Scheduler scheduler;
    private final CatalogReportRepository reports;

    @GetMapping("/scheduler/jobs")
    public List<Map<String, Object>> jobs() throws SchedulerException {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String group : scheduler.getJobGroupNames()) {
            for (JobKey key : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(group))) {
                JobDetail job = scheduler.getJobDetail(key);
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("job", key.toString());
                view.put("class", job.getJobClass().getSimpleName());
                view.put("description", job.getDescription());
                view.put("defaults", new TreeMap<>(job.getJobDataMap().getWrappedMap()));
                List<Map<String, Object>> triggers = new ArrayList<>();
                for (Trigger trigger : scheduler.getTriggersOfJob(key)) {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("trigger", trigger.getKey().toString());
                    t.put("state", scheduler.getTriggerState(trigger.getKey()).name());
                    t.put("nextFireTime", trigger.getNextFireTime());
                    t.put("previousFireTime", trigger.getPreviousFireTime());
                    t.put("misfireInstruction", trigger.getMisfireInstruction());
                    t.put("overrides", new TreeMap<>(trigger.getJobDataMap().getWrappedMap()));
                    if (trigger instanceof CronTrigger cron) {
                        t.put("cron", cron.getCronExpression());
                        t.put("timeZone", cron.getTimeZone().getID());
                    }
                    triggers.add(t);
                }
                view.put("triggers", triggers);
                result.add(view);
            }
        }
        return result;
    }

    /** Запустити завдання зараз (поза розкладом) — з параметрами за замовчуванням із JobDetail. */
    @PostMapping("/scheduler/jobs/{group}/{name}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> run(@PathVariable String group, @PathVariable String name) throws SchedulerException {
        JobKey key = JobKey.jobKey(name, group);
        if (!scheduler.checkExists(key)) {
            throw new ResourceNotFoundException("Job " + key + " not found");
        }
        scheduler.triggerJob(key);
        return Map.of("triggered", key.toString());
    }

    @GetMapping("/reports")
    public List<CatalogReport> reports() {
        return reports.findTop20ByOrderByPeriodStartDescIdDesc();
    }
}
