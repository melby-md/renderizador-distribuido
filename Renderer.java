package tracer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.stream.IntStream;
import static tracer.Math3D.*;

class Renderer {
	private static final int MAX_BOUNCES = 5;
	private static final int MIN_BOUNCES = 3;

	static Scene scene;

	static Ray cameraRay(int x, int y, Random rng) {
		Vec3 forward = scene.lookAt.sub(scene.camera).normalized();
		Vec3 right = forward.cross(scene.up).normalized();
		Vec3 trueUp = right.cross(forward);

		double fov = Math.toRadians(90);
		double viewportHeight = 2.0 * Math.tan(fov * 0.5);
		double viewportWidth = viewportHeight * ((double) scene.width / scene.height);

		Vec3 viewportU = right.mul(viewportWidth);
		Vec3 viewportV = trueUp.mul(viewportHeight);
		Vec3 bottomLeft = scene.camera.add(forward)
				.sub(viewportU.mul(0.5))
				.sub(viewportV.mul(0.5));

		double[] r = rng.next2();
		r[0] *= 2;
		r[1] *= 2;
		double dx = r[0] < 1.0 ? Math.sqrt(r[0]) - 1.0 : 1.0 - Math.sqrt(2.0 - r[0]);
		double dy = r[1] < 1.0 ? Math.sqrt(r[1]) - 1.0 : 1.0 - Math.sqrt(2.0 - r[1]);

		// Shader coordinates start at the bottom-left; BufferedImage starts at top-left.
		double px = x + 0.5 + dx;
		double py = scene.height - y - 0.5 + dy;

		Vec3 world = bottomLeft
				.add(viewportU.mul(px / scene.width))
				.add(viewportV.mul(py / scene.height));
		return new Ray(scene.camera, world.sub(scene.camera).normalized());
	}

	static Vec3 trace(Ray initialRay, Random rng) {
		Ray ray = initialRay;
		Vec3 radiance = Vec3.ZERO;
		Vec3 throughput = Vec3.ONE;

		for (int bounce = 0; bounce < MAX_BOUNCES; bounce++) {
			Scene.Hit hit = scene.intersect(ray);

			if (hit == null) {
				radiance = radiance.add(new Vec3(.5, .5, .5).mul(throughput));
				break;
			}

			Scene.Material material = scene.materials.get(hit.materialIndex());
			Vec3 normal = hit.normal();
			if (ray.direction().dot(normal) > 0) normal = normal.negate();

			Basis basis = new Basis(normal);
			Vec3 v = basis.toLocal(ray.direction().negate());
			BsdfSample sample = sampleBsdf(v, material, rng);

			radiance = radiance.add(material.emission().mul(throughput));
			throughput = throughput.mul(sample.throughput());

			// Roleta russa
			if (bounce >= MIN_BOUNCES) {
				double survivalProbability = Math.max(throughput.x(), Math.max(throughput.y(), throughput.z()));

				if (rng.next() < (1 - survivalProbability)) break;
				throughput = throughput.div(survivalProbability);
			}

			ray = new Ray(hit.point(), basis.toWorld(sample.direction()).normalized());
		}
		return radiance;
	}

	private static record BsdfSample(Vec3 throughput, Vec3 direction) {}

	private static BsdfSample sampleBsdf(Vec3 v, Scene.Material material, Random rng) {
		double diffuseWeight = 1.0 - material.metallic();
		double glossyWeight = 1.0;
		double sumWeights = diffuseWeight + glossyWeight;
		diffuseWeight /= sumWeights;
		glossyWeight /= sumWeights;

		Vec3 l;
		if (rng.next() < diffuseWeight) {
			l = cosineWeightedSample(rng);
		} else {
			Vec3 microfacet = ggxVndfSample(v, material.roughness(), rng);
			l = reflect(v.negate(), microfacet);
		}

		double diffusePdf = Math.max(l.z(), 0.0) / Math.PI;
		double glossyPdf = ggxVndfPdf(v, l, material.roughness());
		double pdf = diffusePdf * diffuseWeight + glossyPdf * glossyWeight;

		// Match the shader's GGX / Smith / Schlick evaluation.
		double alpha = material.roughness() * material.roughness();
		double alpha2 = alpha * alpha;
		Vec3 h = v.add(l).normalized();

		double denom = h.z() * h.z() * (alpha2 - 1.0) + 1.0;
		double ndf = alpha2 / (Math.PI * denom * denom);

		double visV = l.z() * Math.sqrt(v.z() * v.z() * (1.0 - alpha2) + alpha2);
		double visL = v.z() * Math.sqrt(l.z() * l.z() * (1.0 - alpha2) + alpha2);
		double visibility = 0.5 / Math.max(visV + visL, 1e-12);

		double dielectricF0 = Math.pow((1.0 - material.ior())
				/ (1.0 + material.ior()), 2.0);
		Vec3 f0 = mix(new Vec3(dielectricF0, dielectricF0, dielectricF0),
					  material.albedo(), material.metallic());
		double cosine = clamp(h.dot(v), 0, 1);
		Vec3 fresnel = f0.add(Vec3.ONE.sub(f0).mul(Math.pow(1.0 - cosine, 5.0)));

		Vec3 diffuse = Vec3.ONE.sub(fresnel).mul(material.albedo())
				.mul((1.0 - material.metallic()) / Math.PI);
		Vec3 specular = fresnel.mul(visibility * ndf);
		Vec3 bsdf = specular.add(diffuse);

		if (pdf <= 1e-15 || !Double.isFinite(pdf))
			return new BsdfSample(Vec3.ZERO, l);

		Vec3 throughput = bsdf.mul(Math.max(l.z(), 0.0) / pdf);
		return new BsdfSample(throughput, l);
	}

	private static Vec3 cosineWeightedSample(Random rng) {
		double[] r = rng.next2();
		double phi = 2.0 * Math.PI * r[0];
		double sinTheta = Math.sqrt(r[1]);
		double cosTheta = Math.sqrt(1.0 - r[1]);
		return new Vec3(Math.cos(phi) * sinTheta,
						Math.sin(phi) * sinTheta, cosTheta);
	}

	private static Vec3 ggxVndfSample(Vec3 v, double roughness, Random rng) {
		double alpha = roughness * roughness;
		double[] r = rng.next2();

		Vec3 vh = new Vec3(alpha * v.x(), alpha * v.y(), v.z()).normalized();
		double phi = 2.0 * Math.PI * r[0];
		double z = (1.0 - r[1]) * (1.0 + vh.z()) - vh.z();
		double sinTheta = Math.sqrt(clamp(1.0 - z * z, 0.0, 1.0));

		Vec3 cap = new Vec3(sinTheta * Math.cos(phi),
							sinTheta * Math.sin(phi), z);
		Vec3 h = cap.add(vh);
		return new Vec3(h.x() * alpha, h.y() * alpha, h.z()).normalized();
	}

	private static double ggxVndfPdf(Vec3 v, Vec3 l, double roughness) {
		Vec3 h = v.add(l).normalized();
		double alpha = roughness * roughness;
		double alpha2 = alpha * alpha;

		double denom = h.z() * h.z() * (alpha2 - 1.0) + 1.0;
		double ndf = alpha2 / (Math.PI * denom * denom);
		double visibility = 1.0 / (v.z()
				+ Math.sqrt(alpha2 + (1.0 - alpha2) * v.z() * v.z()));
		return ndf * visibility * 0.5;
	}

	private static int toByte(double x) {
		return (int) Math.round(clamp(x, 0, 1) * 255);
	}

	public static BufferedImage render(Scene _scene) throws Exception {
		long start = System.nanoTime();

		scene = _scene;

		int[] pixels = new int[scene.width * scene.height];

		IntStream.range(0, scene.height).parallel().forEach(y -> {
			for (int x = 0; x < scene.width; x++) {
				Vec3 sum = Vec3.ZERO;
				for (int sample = 0; sample < scene.samples; sample++) {
					Random rng = new Random(x, y, sample);
					Ray ray = cameraRay(x, y, rng);
					sum = sum.add(trace(ray, rng));
				}

				Vec3 linear = sum.div(scene.samples).max(0);
				Vec3 mapped = linear.div(linear.add(1)).pow(1.0 / 2.2);

				int r = toByte(mapped.x());
				int g = toByte(mapped.y());
				int b = toByte(mapped.z());
				pixels[y * scene.width + x] = (255 << 24) | (r << 16) | (g << 8) | b;
			}
		});

		BufferedImage image = new BufferedImage(scene.width, scene.height, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, scene.width, scene.height, pixels, 0, scene.width);

		double seconds = (System.nanoTime() - start) / 1e9;
		System.out.printf("Rendered %dx%d at %d spp to %s in %.2f seconds%n",
				scene.width, scene.height, scene.samples, "test.png", seconds);

		return image;
	}
}
