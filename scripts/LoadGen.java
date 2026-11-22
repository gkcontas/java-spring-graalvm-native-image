import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Closed-loop load: each thread sends a request, waits for the answer, sends
 * the next. Single-file program, so it runs as "java scripts/LoadGen.java".
 *
 * Usage: LoadGen <baseUrl> <maxId> <threads> <requests|Ns>
 *   1000  -> exactly 1000 requests in total (cold start phase)
 *   30s   -> as many as fit in 30 seconds (steady state)
 * Prints one JSON line: requests, errors, seconds, rps, p50, p99, max (ms).
 */
public class LoadGen {

    public static void main(String[] args) throws Exception {
        String base = args[0];
        int maxId = Integer.parseInt(args[1]);
        int threads = Integer.parseInt(args[2]);
        boolean timed = args[3].endsWith("s");
        long limit = Long.parseLong(timed ? args[3].substring(0, args[3].length() - 1) : args[3]);

        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        AtomicLong budget = new AtomicLong(limit);
        AtomicLong errors = new AtomicLong();
        long deadline = System.nanoTime() + Duration.ofSeconds(limit).toNanos();
        List<long[]> perThread = new ArrayList<>();
        List<Thread> workers = new ArrayList<>();
        int[] counts = new int[threads];

        long start = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            long[] samples = new long[timed ? 1_000_000 : (int) limit];
            perThread.add(samples);
            int index = t;
            Thread worker = new Thread(() -> {
                int n = 0;
                while (timed ? System.nanoTime() < deadline : budget.getAndDecrement() > 0) {
                    long id = 1 + ThreadLocalRandom.current().nextInt(maxId);
                    HttpRequest request = HttpRequest.newBuilder(
                            URI.create(base + "/api/products/" + id + "/quote?region=SP")).GET().build();
                    long t0 = System.nanoTime();
                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() != 200) {
                            errors.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                    if (n < samples.length) {
                        samples[n++] = System.nanoTime() - t0;
                    }
                }
                counts[index] = n;
            });
            worker.start();
            workers.add(worker);
        }
        for (Thread w : workers) {
            w.join();
        }
        double seconds = (System.nanoTime() - start) / 1e9;

        int total = Arrays.stream(counts).sum();
        long[] all = new long[total];
        int pos = 0;
        for (int t = 0; t < threads; t++) {
            System.arraycopy(perThread.get(t), 0, all, pos, counts[t]);
            pos += counts[t];
        }
        Arrays.sort(all);
        System.out.printf(java.util.Locale.ROOT,
                "{\"requests\":%d,\"errors\":%d,\"seconds\":%.2f,\"rps\":%.0f,\"p50\":%.2f,\"p99\":%.2f,\"max\":%.2f}%n",
                total, errors.get(), seconds, total / seconds, ms(all, 0.50), ms(all, 0.99), all[total - 1] / 1e6);
    }

    private static double ms(long[] sorted, double q) {
        return sorted[(int) Math.min(sorted.length - 1, Math.floor(q * sorted.length))] / 1e6;
    }
}
