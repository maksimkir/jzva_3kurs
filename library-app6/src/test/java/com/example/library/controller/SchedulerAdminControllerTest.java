package com.example.library.controller;

import com.example.library.exception.ResourceNotFoundException;
import com.example.library.repository.CatalogReportRepository;
import com.example.library.scheduling.QuartzJobsConfig;
import com.example.library.scheduling.SchedulingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerAdminControllerTest {

    @Mock private Scheduler scheduler;
    @Mock private CatalogReportRepository reports;
    @InjectMocks private SchedulerAdminController controller;

    private final QuartzJobsConfig config = new QuartzJobsConfig(
            new SchedulingProperties("Europe/Kyiv", 60, "0 0 6 * * ?", "0 0 7 ? * MON"));

    @Test
    void run_existingJob_triggersIt() throws Exception {
        when(scheduler.checkExists(QuartzJobsConfig.REPORT_JOB)).thenReturn(true);

        Map<String, String> result = controller.run("reports", "catalogReport");

        assertThat(result).containsEntry("triggered", "reports.catalogReport");
        verify(scheduler).triggerJob(QuartzJobsConfig.REPORT_JOB);
    }

    @Test
    void run_unknownJob_throwsNotFound() throws Exception {
        when(scheduler.checkExists(JobKey.jobKey("nope", "reports"))).thenReturn(false);

        assertThatThrownBy(() -> controller.run("reports", "nope")).isInstanceOf(ResourceNotFoundException.class);
        verify(scheduler, never()).triggerJob(any(JobKey.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void jobs_listsJobsWithTriggersOverridesAndTimeZone() throws Exception {
        when(scheduler.getJobGroupNames()).thenReturn(List.of("reports"));
        when(scheduler.getJobKeys(any(GroupMatcher.class))).thenReturn(Set.of(QuartzJobsConfig.REPORT_JOB));
        when(scheduler.getJobDetail(QuartzJobsConfig.REPORT_JOB)).thenReturn(config.catalogReportJobDetail());
        doReturn(List.of(config.catalogReportWeeklyTrigger())).when(scheduler).getTriggersOfJob(QuartzJobsConfig.REPORT_JOB);
        when(scheduler.getTriggerState(QuartzJobsConfig.WEEKLY_REPORT_TRIGGER)).thenReturn(Trigger.TriggerState.NORMAL);

        List<Map<String, Object>> result = controller.jobs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("job", "reports.catalogReport").containsEntry("class", "CatalogReportJob");
        Map<String, Object> trigger = ((List<Map<String, Object>>) result.get(0).get("triggers")).get(0);
        assertThat(trigger).containsEntry("trigger", "reports.catalogReportWeekly")
                .containsEntry("timeZone", "Europe/Kyiv")
                .containsEntry("state", "NORMAL");
        assertThat((Map<String, Object>) trigger.get("overrides")).containsEntry("period", "WEEKLY");
    }
}
