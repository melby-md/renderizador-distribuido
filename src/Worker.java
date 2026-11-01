package tracer;

import java.io.*;
import java.net.Socket;

public class Worker {
	public static void main(String[] args) throws Exception {
		String host = args.length > 0 ? args[0] : "localhost";
		int port = args.length > 1 ? Integer.parseInt(args[1]) : 8081;

		try (Socket socket = new Socket(host, port);
			 DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
			while (true) {
				int sceneLength;
				try {
					sceneLength = in.readInt();
				} catch (EOFException e) {
					return;
				}

				if (sceneLength < 0 || sceneLength > 64 * 1024 * 1024)
					throw new IOException("Invalid scene size: " + sceneLength);
				byte[] sceneXml = new byte[sceneLength];
				in.readFully(sceneXml);
				int startY = in.readInt();
				int endY = in.readInt();

				Scene scene = new Scene(new ByteArrayInputStream(sceneXml));
				if (startY < 0 || endY <= startY || endY > scene.height)
					throw new IOException("Invalid row range: " + startY + "-" + endY);
				int[] pixels = Renderer.renderRows(scene, startY, endY);
				out.writeInt(startY);
				out.writeInt(endY);
				out.writeInt(pixels.length);
				for (int pixel : pixels) out.writeInt(pixel);
				out.flush();
			}
		}
	}
}
