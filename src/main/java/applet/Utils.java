/**
 * 
 */
package applet;

import java.io.File;

/**
 * @author stefano
 *
 */
public class Utils {

	public static void delete(String path) {
		if (null == path || (path = path.trim()).isEmpty())
			throw new IllegalArgumentException("Illegal 'path' argument in Utils.delete(String): " + path);
		delete(new File(path));
	}

	public static void delete(File file) {
		if (null == file)
			throw new IllegalArgumentException("Illegal 'file' argument in Utils.delete(File): " + file);
		if (file.exists()) {
			if (file.isDirectory()) {
				for (File child : file.listFiles()) {
					delete(child);
				}
			}
			file.delete();
		}
	}

}
