package tracer;

class Math3D {
	record Vec3(double x, double y, double z) {
		static final Vec3 ZERO = new Vec3(0, 0, 0);
		static final Vec3 ONE = new Vec3(1, 1, 1);

		Vec3 add(Vec3 b) { return new Vec3(x + b.x, y + b.y, z + b.z); }
		Vec3 add(double s) { return new Vec3(x + s, y + s, z + s); }
		Vec3 sub(Vec3 b) { return new Vec3(x - b.x, y - b.y, z - b.z); }
		Vec3 mul(double s) { return new Vec3(x * s, y * s, z * s); }
		Vec3 mul(Vec3 b) { return new Vec3(x * b.x, y * b.y, z * b.z); }
		Vec3 div(double s) { return mul(1.0 / s); }
		Vec3 div(Vec3 b) { return new Vec3(x / b.x, y / b.y, z / b.z); }
		double dot(Vec3 b) { return x * b.x + y * b.y + z * b.z; }
		Vec3 cross(Vec3 b) {
			return new Vec3(y * b.z - z * b.y,
							z * b.x - x * b.z,
							x * b.y - y * b.x);
		}
		double lengthSquared() { return dot(this); }
		double length() { return Math.sqrt(lengthSquared()); }
		Vec3 normalized() {
			double len = length();
			return len > 1e-15 ? div(len) : ZERO;
		}
		Vec3 negate() { return mul(-1); }
		Vec3 max(double v) { return new Vec3(Math.max(x, v), Math.max(y, v), Math.max(z, v)); }
		Vec3 pow(double p) { return new Vec3(Math.pow(x, p), Math.pow(y, p), Math.pow(z, p)); }
		Vec3 exp() { return new Vec3(Math.exp(x), Math.exp(y), Math.exp(z)); }
		double component(int i) { return i == 0 ? x : i == 1 ? y : z; }
	}

	record Ray(Vec3 origin, Vec3 direction) {
		Vec3 at(double t) { return origin.add(direction.mul(t)); }
	}

	static double clamp(double x, double lo, double hi) {
		return Math.max(lo, Math.min(hi, x));
	}

	static double mix(double a, double b, double t) {
		return a * (1 - t) + b * t;
	}

	static Vec3 mix(Vec3 a, Vec3 b, double t) {
		return a.mul(1 - t).add(b.mul(t));
	}

	static Vec3 reflect(Vec3 incident, Vec3 normal) {
		return incident.sub(normal.mul(2 * incident.dot(normal)));
	}

	// Local-to-world orthonormal basis. Its columns are x, y and z.
	static final class Basis {
		final Vec3 x, y, z;

		Basis(Vec3 normal) {
			z = normal;
			Vec3 a = Math.abs(z.x()) > 0.9
					? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
			y = z.cross(a).normalized();
			x = y.cross(z);
		}

		Vec3 toWorld(Vec3 v) {
			return x.mul(v.x()).add(y.mul(v.y())).add(z.mul(v.z()));
		}

		Vec3 toLocal(Vec3 v) {
			return new Vec3(v.dot(x), v.dot(y), v.dot(z));
		}
	}

	// Port of the shader's PCG2D generator. Java int overflow is intentional.
	static final class Random {
		private int x, y;

		Random(int pixelX, int pixelY, int sampleIndex) {
			int frame = sampleIndex << 16;
			x = pixelX ^ frame;
			y = pixelY ^ frame;
		}

		double[] next2() {
			x = 1664525 * x + 1013904223;
			y = 1664525 * y + 1013904223;
			x += 1664525 * y;
			y += 1664525 * x;
			x ^= (x >>> 16);
			y ^= (y >>> 16);
			x += 1664525 * y;
			y += 1664525 * x;
			x ^= (x >>> 16);
			y ^= (y >>> 16);
			return new double[] {
				Integer.toUnsignedLong(x) * 0x1.0p-32,
				Integer.toUnsignedLong(y) * 0x1.0p-32
			};
		}

		double next() { return next2()[0]; }
	}
}
