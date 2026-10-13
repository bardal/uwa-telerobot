
// Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Sun Sep 12 12:01:38 1999
// Version       : $Id: Jar.java,v 1.2 1999/12/18 02:43:13 bdalton Exp $ 
// Time-stamp    : "1999-09-04 17:32:06 student"

// {{{ GNU General Public License
//  This program is free software; you can redistribute it and/or
//  modify it under the terms of the GNU General Public License
//  as published by the Free Software Foundation; either version 2
//  of the License, or (at your option) any later version.

//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.

//  You should have received a copy of the GNU General Public License
//  along with this program; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
// }}}


package au.edu.uwa.mech.util;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

import au.edu.uwa.mech.io.CopyStream;

public class Jar {
  
  public static void setMainClass(String filename, String className) throws IOException
  {
    JarFile jarFile = new JarFile(filename);
    
    Manifest manifest = jarFile.getManifest();
    if (manifest == null){
      manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    }
    
    //manifest.write(System.out);
    manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, className);

    System.out.println("New manifest is");
    manifest.write(System.out);
    jarFile.close();

    updateManifest(filename, manifest);
  }

  public static void updateManifest(String filename, Manifest manifest) throws IOException
  {
    System.out.println("updating manifest in "+filename);
    
    File origFile = new File(filename);
    File workingFile = File.createTempFile(origFile.getName(),".tmp");
    JarFile jarFile = new JarFile(origFile);
    
    FileOutputStream fOut= new FileOutputStream(workingFile);
    JarOutputStream jOut=new JarOutputStream(new BufferedOutputStream(fOut), manifest);

    for (Enumeration e=jarFile.entries();e.hasMoreElements();){
      ZipEntry nextEntry=(ZipEntry)e.nextElement();
      //System.out.println(nextEntry);
      //System.out.println(JarFile.MANIFEST_NAME);
      if (nextEntry.getName().equals(JarFile.MANIFEST_NAME))
        continue;
      jOut.putNextEntry(nextEntry);
      CopyStream.copy(jarFile.getInputStream(nextEntry), jOut);
      jOut.closeEntry();
    }
    
    jOut.close();
    jarFile.close();

    origFile.delete();
    workingFile.renameTo(origFile);

    System.out.println("Updated manifest");
  }
}



