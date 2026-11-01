package tracer;
import java.util.ArrayList;
import java.util.HashMap;
import static tracer.Math3D.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.io.InputStream;

final class Scene {
	record Material(Vec3 albedo, Vec3 emission, double ior, double metallic, double roughness) {}

	record Sphere(int materialIndex, Vec3 center, double radius) {}
	record Hit(double distance, Vec3 point, Vec3 normal, int materialIndex) {}

	ArrayList<Material> materials = new ArrayList<>();
	ArrayList<Sphere> spheres = new ArrayList<>();

	int width, height, samples;
	Vec3 camera, lookAt, up;

	Vec3 parseVec3(String s) {
		String[] values = s.trim().split("\\s+");

		double x = Double.parseDouble(values[0]);
		double y = Double.parseDouble(values[1]);
		double z = Double.parseDouble(values[2]);

		return new Vec3(x, y, z);
	}

	Scene(InputStream input) throws Exception {
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc = builder.parse(input);
		Element scene = doc.getDocumentElement();

		Element settings = (Element)doc.getElementsByTagName("settings").item(0);
		width = Integer.parseInt(settings.getAttribute("width"));
		height = Integer.parseInt(settings.getAttribute("height"));
		samples = Integer.parseInt(settings.getAttribute("samples"));

		camera = parseVec3(settings.getAttribute("camera"));
		lookAt = parseVec3(settings.getAttribute("lookAt"));
		up = parseVec3(settings.getAttribute("up"));

		HashMap<String, Integer> materialMap = new HashMap<>();

		NodeList elements = doc.getElementsByTagName("material");

		for (int i = 0; i < elements.getLength(); i++) {
			Element element = (Element)elements.item(i);

			String name = element.getAttribute("id");
			Vec3 color = parseVec3(element.getAttribute("color"));
			Vec3 emission = parseVec3(element.getAttribute("emission"));
			double ior = Double.parseDouble(element.getAttribute("ior"));
			double roughness = Double.parseDouble(element.getAttribute("roughness"));
			double metallic = Double.parseDouble(element.getAttribute("metallic"));

			Material material = new Material(color, emission, ior, metallic, roughness);

			int idx = materials.size();

			materials.add(material);
			materialMap.put(name, idx);
		}

		elements = doc.getElementsByTagName("sphere");

		for (int i = 0; i < elements.getLength(); i++) {
			Element element = (Element)elements.item(i);

			double radius = Double.parseDouble(element.getAttribute("radius"));
			Vec3 pos = parseVec3(element.getAttribute("position"));
			String material = element.getAttribute("material");

			int matIdx = materialMap.get(material);

			Sphere sphere = new Sphere(matIdx, pos, radius);

			spheres.add(sphere);
		}
	}

	Hit intersect(Ray ray) {
		Hit closest = null;
		double minDistance = Double.POSITIVE_INFINITY;

		for (Sphere sphere : spheres) {
			Vec3 oc = sphere.center().sub(ray.origin());
			double h = ray.direction().dot(oc);
			double c = oc.dot(oc) - sphere.radius() * sphere.radius();
			double delta = h * h - c;

			if (delta < 0.001) continue;

			double root = Math.sqrt(delta);
			double t = h - root;
			if (t < 0.001) {
				t = h + root;
				if (t < 0.001) continue;
			}

			if (t < minDistance) {
				minDistance = t;
				Vec3 point = ray.at(t);
				Vec3 normal = point.sub(sphere.center()).div(sphere.radius());
				closest = new Hit(t, point, normal, sphere.materialIndex());
			}
		}

		return closest;
	}
}
