package com.geoxp.oss;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class DirectoryHierarchyKeyStore extends KeyStore {
  
  private final File directory;
  
  public DirectoryHierarchyKeyStore(String directory) {
    try {
      this.directory = new File(directory).getCanonicalFile();
    } catch (IOException ioe) {
      throw new RuntimeException("Unable to determine canonical path.");
    }
    
    if (!this.directory.exists() || !this.directory.isDirectory()) {
      throw new RuntimeException("Invalid directory '" + this.directory.getAbsolutePath() + "'");
    }
  }
  
  @Override
  public byte[] getSecret(String name, String fingerprint) throws OSSException {    
    //
    // Sanitize name
    //
    
    name = sanitizeSecretName(name);
    
    File root = getSecretFile(name);
    
    File secretFile = new File(root.getAbsolutePath() + ".secret");
    File aclFile = new File(root.getAbsolutePath() + ".acl");
    
    //
    // Check if secret exists
    //
    
    if (!secretFile.exists() || !secretFile.isFile() || !aclFile.exists() || !aclFile.isFile()) {
      throw new OSSException("Missing secret or ACL file.");
    }

    //
    // Check ACLs
    //

    // Sanitize fingerprint
    
    if (null == fingerprint) {
      fingerprint = "";
    }
    
    fingerprint = fingerprint.toLowerCase().replaceAll("[^0-9a-f]","");
    
    boolean authorized = false;
    
    try {
      BufferedReader br = new BufferedReader(new FileReader(aclFile));
      
      while(true) {
        String line = br.readLine();
        
        if (null == line) {
          break;
        }
        
        String acl = line.toLowerCase().replaceAll("[^0-9a-f#*]", "");
        
        if ("*".equals(acl) || fingerprint.equals(acl)) {
          authorized = true;
          break;
        }
      }
      
      br.close();      
    } catch (IOException ioe) {
      throw new OSSException(ioe);
    }
    
    if (!authorized) {
      throw new OSSException("Access denied.");
    }
    
    //
    // Read secret
    //
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream((int) secretFile.length());
    
    byte[] buf = new byte[1024];
    
    try {
      InputStream is = new FileInputStream(secretFile);

      do {
        int len = is.read(buf);
        
        if (-1 == len) {
          break;
        }
        
        baos.write(buf, 0, len);
      } while(true);
      
      is.close();
    } catch (IOException ioe) {
      throw new OSSException(ioe);
    }
    
    return baos.toByteArray();
  }
  
  @Override
  public void putSecret(String name, byte[] secret) throws OSSException {
    //
    // Sanitize name
    //
    
    name = sanitizeSecretName(name);
    
    File root = getSecretFile(name);
    
    File secretFile = new File(root.getAbsolutePath() + ".secret");
    
    if (secretFile.exists()) {
      throw new OSSException("Secret '" + name + "' already exists.");
    }
        
    //
    // Create hierarchy
    //
    
    if (secretFile.getParentFile().exists() && !secretFile.getParentFile().isDirectory()) {
      throw new OSSException("Secret path already exists and is not a directory.");
    }
    
    if (!secretFile.getParentFile().exists() && !secretFile.getParentFile().mkdirs()) {
      throw new OSSException("Unable to create path to secret file.");
    }
    
    try {
      
      OutputStream os = new FileOutputStream(secretFile);      
      os.write(secret);      
      os.close();
      
    } catch (IOException ioe) {
      throw new OSSException(ioe);
    }
  }  
  
  /**
   * Retrieve secret file from secret name
   * 
   * @param name
   * @return
   */
  private File getSecretFile(String name) {
    //
    // Sanitize name
    //
    
    name = sanitizeSecretName(name);
    
    //
    // Replace '.' with '/'
    //
    
    String[] tokens = name.split("\\.");
    
    File f;
    
    f = this.directory.getAbsoluteFile();
      
    for (int i = 0; i < tokens.length; i++) {
      f = new File(f, tokens[i]);
    }

    return f;
  }
}
