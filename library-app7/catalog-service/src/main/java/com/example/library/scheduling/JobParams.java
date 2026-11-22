package com.example.library.scheduling;

import org.quartz.JobDataMap;
import org.quartz.JobExecutionException;

import java.util.Set;
import java.util.TreeSet;

/**
 * Явне читання параметрів завдання з (об'єднаного) JobDataMap.
 * <ul>
 *   <li>Значення за замовчуванням живуть в ОДНОМУ місці — у JobDetail (QuartzJobsConfig), тут їх немає.</li>
 *   <li>Відсутній ключ -> JobExecutionException з назвою ключа (а не NPE/0 «з нізвідки»).</li>
 *   <li>Невідомий ключ (одруківка в тригері, напр. "topAuthor") -> JobExecutionException зі списком очікуваних ключів,
 *       інакше перевизначення мовчки ігнорувалося б.</li>
 *   <li>Невалідне число -> JobExecutionException з поясненням.</li>
 * </ul>
 * Помилки параметрів не повторюються (refireImmediately=false) — повтор їх не виправить.
 */
public final class JobParams {

    private final JobDataMap data;
    private final String jobName;

    private JobParams(JobDataMap data, String jobName) {
        this.data = data;
        this.jobName = jobName;
    }

    public static JobParams of(JobDataMap merged, String jobName, Set<String> allowedKeys) throws JobExecutionException {
        Set<String> unknown = new TreeSet<>(merged.keySet());
        unknown.removeAll(allowedKeys);
        if (!unknown.isEmpty()) {
            throw new JobExecutionException(jobName + ": unknown JobDataMap key(s) " + unknown
                    + " — expected only " + new TreeSet<>(allowedKeys) + " (typo in trigger/job data?)");
        }
        return new JobParams(merged, jobName);
    }

    public String requiredString(String key) throws JobExecutionException {
        Object value = data.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new JobExecutionException(jobName + ": required JobDataMap key '" + key + "' is missing");
        }
        return value.toString().trim();
    }

    public int requiredInt(String key, int min) throws JobExecutionException {
        String raw = requiredString(key);
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new JobExecutionException(jobName + ": JobDataMap key '" + key + "' must be an integer, but was '" + raw + "'");
        }
        if (value < min) {
            throw new JobExecutionException(jobName + ": JobDataMap key '" + key + "' must be >= " + min + ", but was " + value);
        }
        return value;
    }

    public <E extends Enum<E>> E requiredEnum(String key, Class<E> type) throws JobExecutionException {
        String raw = requiredString(key);
        try {
            return Enum.valueOf(type, raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new JobExecutionException(jobName + ": JobDataMap key '" + key + "' has unsupported value '" + raw + "'");
        }
    }
}
