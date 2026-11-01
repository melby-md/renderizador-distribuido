package tracer;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.regex.*;
import javax.imageio.ImageIO;
import com.sun.net.httpserver.*;
import java.nio.file.Path;
import java.nio.file.Files;

public class Server {
	static byte[] html_data;

	public static void main(String[] args) throws IOException {

		Path html_file = Path.of("index.html");
		html_data = Files.readAllBytes(html_file);

		HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

		server.createContext("/", Server::index);
		server.createContext("/render", Server::render);

		server.start();
		System.out.println("http://localhost:8080");
	}

	static void index(HttpExchange exchange) throws IOException {

		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");

		exchange.sendResponseHeaders(200, html_data.length);

		try (OutputStream out = exchange.getResponseBody()) {
			out.write(html_data);
		}
	}

	static void render(HttpExchange exchange) throws IOException {
		InputStream sceneData = exchange.getRequestBody();

		BufferedImage output;
		try {
			Scene scene = new Scene(sceneData);
			output = Renderer.render(scene);
		} catch (Exception e) {
			System.out.println(e);
			throw new IOException();
		}

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ImageIO.write(output, "png", buffer);

		byte[] imgData = buffer.toByteArray();

		exchange.getResponseHeaders().set("Content-Type", "image/png");
		exchange.sendResponseHeaders(200, imgData.length);

		try (OutputStream out = exchange.getResponseBody()) {
			out.write(imgData);
		}
	}
}
