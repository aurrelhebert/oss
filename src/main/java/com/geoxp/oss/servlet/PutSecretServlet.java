package com.geoxp.oss.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bouncycastle.util.encoders.Base64;

import com.geoxp.oss.CryptoHelper;
import com.geoxp.oss.OSS;
import com.geoxp.oss.OSS.OSSToken;
import com.geoxp.oss.OSSException;
import com.google.inject.Singleton;

@Singleton
public class PutSecretServlet extends HttpServlet {
  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    
    if (!OSS.isInitialized()) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Open Secret Server not yet initialized.");
      return;
    }

    //
    // Extract token
    //
    
    String b64token = req.getParameter("token");
    
    //
    // Decode it from base64
    //
    
    byte[] token = Base64.decode(b64token);
    
    //
    // Extract wrapped init token and sealed AES key
    //
    
    byte[] wrappedtoken = CryptoHelper.decodeNetworkString(token, 0);
    byte[] sealedaeskey = CryptoHelper.decodeNetworkString(token, wrappedtoken.length + 4);
    
    //
    // Unseal AES key
    //
    
    byte[] aeskey = CryptoHelper.decryptRSA(OSS.getSessionRSAPrivateKey(), sealedaeskey);
    
    //
    // Unwrap init token
    //
    
    byte[] inittoken = CryptoHelper.unwrapAES(aeskey, wrappedtoken);
    
    //
    // Check OSS Token
    //
    
    OSS.OSSToken osstoken = null;
    
    try {
      osstoken = OSS.checkToken(inittoken);
    } catch (OSSException osse) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, osse.getMessage());
      return;
    }
    
    //
    // Check that key can store secrets
    //
    
    if (!OSS.checkPutSecretSSHKey(osstoken.getKeyblob())) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "SSH Key cannot store a secret.");
      return;  
    }
    
    //
    // Extract secretname and secret content
    //
    
    byte[] secretname = CryptoHelper.decodeNetworkString(osstoken.getSecret(), 0);
    byte[] secret = CryptoHelper.decodeNetworkString(osstoken.getSecret(), secretname.length + 4);
    
    //
    // Attempt to store secret
    //
    
    try {          
      OSS.getKeyStore().putSecret(new String(secretname, "UTF-8"), CryptoHelper.wrapAES(OSS.getMasterSecret(), secret));
    } catch (OSSException e) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      return;
    }
    
    resp.setStatus(HttpServletResponse.SC_OK);
  }
}
