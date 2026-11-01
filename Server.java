package tracer;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import com.sun.net.httpserver.*;

public class Server {
	private static final int HTTP_PORT = 8080;
	private static final int TCP_PORT = 8081;
	private static final int TCP_WORKERS = Math.max(2, Runtime.getRuntime().availableProcessors());
	private static final int ROWS_PER_JOB = 16;
	private static final BlockingQueue<RenderJob> renderQueue = new LinkedBlockingQueue<>();
	static byte[] html_data;

	public static void main(String[] args) throws IOException {
		html_data = Files.readAllBytes(Path.of("index.html"));
		HttpServer httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
		httpServer.createContext("/", Server::index);
		httpServer.createContext("/render", Server::render);
		httpServer.start();
		System.out.println("http://localhost:" + HTTP_PORT);

		ExecutorService tcpPool = Executors.newFixedThreadPool(TCP_WORKERS);
		ServerSocket tcpServer = new ServerSocket(TCP_PORT);
		Thread acceptor = new Thread(() -> acceptTcpClients(tcpServer, tcpPool), "tcp-acceptor");
		acceptor.start();
		System.out.println("TCP render workers listening on port " + TCP_PORT);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try { tcpServer.close(); } catch (IOException ignored) {}
			tcpPool.shutdownNow();
			httpServer.stop(0);
		}, "server-shutdown"));
	}

	private static void acceptTcpClients(ServerSocket server, ExecutorService pool) {
		while (!server.isClosed()) {
			try {
				Socket client = server.accept();
				pool.execute(() -> handleTcpWorker(client));
			} catch (RejectedExecutionException e) {
				if (!server.isClosed()) System.err.println("TCP worker pool is full");
			} catch (SocketException e) {
				if (!server.isClosed()) System.err.println("TCP accept failed: " + e.getMessage());
			} catch (IOException e) {
				System.err.println("TCP accept failed: " + e.getMessage());
			}
		}
	}

	private static void handleTcpWorker(Socket client) {
		try (Socket socket = client;
			 DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
			while (true) {
				RenderJob job = renderQueue.take();
				try {
					out.writeInt(1); // job
					out.writeInt(job.sceneXml.length);
					out.write(job.sceneXml);
					out.writeInt(job.startY);
					out.writeInt(job.endY);
					out.flush();

					int startY = in.readInt();
					int endY = in.readInt();
					int pixelCount = in.readInt();
					int expected = job.width * (job.endY - job.startY);
					if (startY != job.startY || endY != job.endY || pixelCount != expected)
						throw new IOException("Invalid worker response");
					int[] pixels = new int[pixelCount];
					for (int i = 0; i < pixelCount; i++) pixels[i] = in.readInt();
					job.result.complete(new RenderResult(startY, endY, pixels));
				} catch (IOException e) {
					job.result.completeExceptionally(e);
					throw e;
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			System.err.println("TCP worker disconnected: " + e.getMessage());
		}
	}

	private static final class RenderJob {
		final byte[] sceneXml;
		final int width, startY, endY;
		final CompletableFuture<RenderResult> result = new CompletableFuture<>();
		RenderJob(byte[] sceneXml, int width, int startY, int endY) {
			this.sceneXml = sceneXml;
			this.width = width;
			this.startY = startY;
			this.endY = endY;
		}
	}
	private record RenderResult(int startY, int endY, int[] pixels) {}

	static void index(HttpExchange exchange) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
		exchange.sendResponseHeaders(200, html_data.length);
		try (OutputStream out = exchange.getResponseBody()) { out.write(html_data); }
	}

	static void render(HttpExchange exchange) throws IOException {
		byte[] sceneXml = exchange.getRequestBody().readAllBytes();
		try {
			Scene scene = new Scene(new ByteArrayInputStream(sceneXml));
			int[] pixels = new int[scene.width * scene.height];
			ArrayList<CompletableFuture<RenderResult>> jobs = new ArrayList<>();
			for (int startY = 0; startY < scene.height; startY += ROWS_PER_JOB) {
				RenderJob job = new RenderJob(sceneXml, scene.width, startY, Math.min(startY + ROWS_PER_JOB, scene.height));
				jobs.add(job.result);
				renderQueue.put(job);
			}
			for (CompletableFuture<RenderResult> future : jobs) {
				RenderResult result = future.get();
				System.arraycopy(result.pixels, 0, pixels, result.startY * scene.width, result.pixels.length);
			}
			BufferedImage image = new BufferedImage(scene.width, scene.height, BufferedImage.TYPE_INT_ARGB);
			image.setRGB(0, 0, scene.width, scene.height, pixels, 0, scene.width);
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			ImageIO.write(image, "png", buffer);
			byte[] data = buffer.toByteArray();
			exchange.getResponseHeaders().set("Content-Type", "image/png");
			exchange.sendResponseHeaders(200, data.length);
			try (OutputStream out = exchange.getResponseBody()) { out.write(data); }
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Render interrupted", e);
		} catch (ExecutionException e) {
			throw new IOException("TCP worker failed", e.getCause());
		} catch (Exception e) {
			throw new IOException("Could not render scene", e);
		}
	}
}
