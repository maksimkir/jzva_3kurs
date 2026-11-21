package com.example.library.scheduling;

import com.example.library.digest.NewBooksDigestService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Set;

/**
 * ЛР6, завдання 1 — дайджест новинок для читачів-підписників.
 * <p>
 * «Легке» завдання: читає параметри з merged JobDataMap і робить ОДИН виклик сервісу; бізнес-логіки й транзакцій тут немає.
 * <p>
 * Стратегія збою: ПОВТОР з обмеженням. Якщо лист не відправився (SMTP тимчасово недоступний), Quartz одразу
 * перезапускає завдання (refireImmediately), але не більше {@value #MAX_RETRIES}-разів (з JobDataMap);
 * потім помилка фіксується, і завдання чекає наступного запуску за розкладом — нескінченного циклу немає.
 * <p>
 * {@code @DisallowConcurrentExecution}: два одночасні запуски могли б взяти ті самі неанонсовані книги
 * і надіслати їх двічі, тому наступний запуск чекає завершення попереднього (у кластері теж — через JDBC store).
 */
@Slf4j
@DisallowConcurrentExecution
public class NewBooksDigestJob implements Job {

    public static final String BATCH_SIZE = "batchSize";
    public static final String RECIPIENTS = "recipients";
    public static final String MAX_RETRIES = "maxRetries";
    static final Set<String> KEYS = Set.of(BATCH_SIZE, RECIPIENTS, MAX_RETRIES);

    private final NewBooksDigestService digestService;

    public NewBooksDigestJob(NewBooksDigestService digestService) {
        this.digestService = digestService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobParams params = JobParams.of(context.getMergedJobDataMap(), "newBooksDigest", KEYS);
        int batchSize = params.requiredInt(BATCH_SIZE, 1);
        String recipients = params.requiredString(RECIPIENTS);
        int maxRetries = params.requiredInt(MAX_RETRIES, 0);

        try {
            digestService.sendDigest(batchSize, recipients);
        } catch (RuntimeException e) {
            throw JobFailures.retryWithLimit(context, e, maxRetries);
        }
    }
}
