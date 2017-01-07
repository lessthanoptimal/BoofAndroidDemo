package org.boofcv.android.recognition;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import org.boofcv.android.misc.UnitsDistance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import boofcv.struct.image.GrayU8;

/**
 * Used to load and save the list of fiducials.  Also loads individual fiducial images.  A
 * file is created which stores a catalog of all fiducials.  Each fiducial has a unique ID.
 * This ID is used to name the file.  The name of the fiducial provided by the user is saved in
 * the file along with its size and units.
 *
 * @author Peter Abeles
 */
public class FiducialManager {

	public static final String TAG = "FiducialManager";

	public static String FILE_NAME = "fiducial_images.txt";
	public static String DIRECTORY_NAME = "fiducials";

	List<Info> list = new ArrayList<>();

	Activity owner;
	Random rand = new Random();

	public FiducialManager(Activity owner) {
		this.owner = owner;

		File directory = owner.getApplicationContext().getDir(DIRECTORY_NAME,Context.MODE_PRIVATE);
		if( directory.exists() ) {
			cleanUpDirectory();
		} else {
			if( !directory.mkdir() ) {
				throw new RuntimeException("Can't create fiducial directory");
			}
		}
	}

	public void addFiducial( GrayU8 image , double sideLength , UnitsDistance units ,
							 String name ) {

		Info info = new Info();
		info.id = selectUniqueId();
		info.sideLength = sideLength;
		info.units = units;
		info.name = name;

		list.add( info );
		saveImage(image, info.id );

		try {
			FileOutputStream outputStream = owner.openFileOutput(FILE_NAME,
					Context.MODE_PRIVATE | Context.MODE_APPEND );
			PrintStream out = new PrintStream(outputStream);
			out.println(info.id+" "+info.sideLength+" "+info.units.name()+" "+info.name);
			outputStream.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private int selectUniqueId() {
		int id = rand.nextInt();

		boolean done = false;
		while( !done ) {
			done = true;
			if (id < 0) id = -id;
			for (Info info : list) {
				if( info.id == id ) {
					id = rand.nextInt();
					done = false;
					break;
				}
			}
		}

		return id;
	}

	public void deleteFiducial( Info which ) {

		if( !removeFromList(list,which) ) {
			Log.d(TAG,"Can't find fiducial in list! "+which.id+" "+which.name);
			return;
		}

		File directory = owner.getApplicationContext().getDir(DIRECTORY_NAME,Context.MODE_PRIVATE);
		if( !new File(directory,which.id+"").delete() ) {
			Log.d(TAG,"Can't delete fiducial image for "+which.id+"  "+which.name);
			return;
		}
		saveList();
	}

	private boolean removeFromList(List<Info> list, Info which) {
		for (int i = 0; i < list.size(); i++) {
			Info info = list.get(i);
			if( info.id == which.id ) {
				list.remove(i);
				return true;
			}
		}
		return false;
	}

	/**
	 * Deletes files which shouldn't be there
	 */
	public void cleanUpDirectory() {

	}

	public void saveList() {
		try {
			FileOutputStream outputStream = owner.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
			PrintStream out = new PrintStream(outputStream);
			for( Info info : list ) {
				// save the name at the end so I don't need to worry about parsing spaces and such
				out.println(info.id+" "+info.sideLength+" "+info.units.name()+" "+info.name);
			}
			outputStream.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void loadList() {
		list.clear();

		try {
			FileInputStream inputStream = owner.openFileInput(FILE_NAME);
			BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));

			while (true) {
				String line = input.readLine();
				if (line == null)
					break;

				Info info = new Info();

				int where = findSpace(line, 0);
				info.id = Integer.parseInt(line.substring(0, where));
				int start = where + 1;
				where = findSpace(line, where + 1);
				info.sideLength = Double.parseDouble(line.substring(start, where));
				start = where + 1;
				where = findSpace(line, where + 1);
				info.units = UnitsDistance.valueOf(line.substring(start, where));
				info.name = line.substring(where + 1, line.length());

				list.add(info);
			}
			inputStream.close();
		} catch( FileNotFoundException ignore) {
			Log.d(TAG,"Fiducial index file not found");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private int findSpace(String line, int start) {
		int where = start;
		while( where < line.length() ) {
			if( line.charAt(where) == ' ') {
				break;
			} else {
				where++;
			}
		}
		return where;
	}

	private void saveImage(GrayU8 image, int id ) {
		try {
			File directory = owner.getApplicationContext().getDir(DIRECTORY_NAME,Context.MODE_PRIVATE);
			PrintStream out = new PrintStream(new File(directory,id+""));

			char column[] = new char[image.width];
			out.println(image.width+" "+image.height);
			for (int y = 0; y < image.height; y++) {
				for (int x = 0; x < image.width; x++) {
					column[x] = (char) image.unsafe_get(x,y);
				}
				out.print(column);
				out.println();
			}
			out.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public GrayU8 loadBinaryImage(int id) {
		try {
			File directory = owner.getApplicationContext().getDir(DIRECTORY_NAME,Context.MODE_PRIVATE);
			BufferedReader input = new BufferedReader(new FileReader(new File(directory,id+"")));

			String words[] = input.readLine().split(" ");

			int width = Integer.parseInt(words[0]);
			int height = Integer.parseInt(words[1]);

			GrayU8 image = new GrayU8(width,height);
			for (int y = 0; y < image.height; y++) {
				String line = input.readLine();
				if( line.length() != width )
					return null;

				for (int x = 0; x < image.width; x++) {
					int value = line.charAt(x);
					image.unsafe_set(x,y,value);
				}
			}
			return image;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public List<Info> copyList() {
		List<Info> copy = new ArrayList<>();

		for( Info info : list ) {
			copy.add( info.copy() );
		}

		return copy;
	}

	public static class Info
	{
		// name of the fiducial
		public String name;
		// unique ID number of the fiducial
		public int id;
		// width of the fiducial
		public double sideLength;
		// units that the width is specified in
		public UnitsDistance units;

		public Info copy() {
			Info info = new Info();
			info.name = name;
			info.id = id;
			info.sideLength = sideLength;
			info.units = units;
			return info;
		}
	}
}
