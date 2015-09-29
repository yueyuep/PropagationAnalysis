package com.vmusco.smf.mutation;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public final class TestingTools {
	
	private TestingTools() {
	}
	
	public static String[] getCurrentCp() {
		ClassLoader cl = ClassLoader.getSystemClassLoader();

        URL[] urls = ((URLClassLoader)cl).getURLs();
        String[] cp = new String[urls.length];
        int i = 0;
        for(URL url: urls){
        	cp[i++] = url.getFile();
        }
        
        return cp;
	}
	
	/**
	 * Return a source folder for a test to compile
	 * Only works if the executable is run from the project base
	 * @param testClass the class object which is in the desired folder (package)
	 * @return
	 */
	public static String[] getTestClassForCurrentProject(Class testClass){
		File f = new File (System.getProperty("user.dir"));
		f = new File(f, "src"+File.separator+"test"+File.separator+"java");
		f = new File(f, testClass.getCanonicalName().replaceAll("\\.", File.separator));
		f = f.getParentFile();
		
		return new String[]{f.getAbsolutePath()};
	}
}