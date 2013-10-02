/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */


import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

import java.net.MalformedURLException;
import java.net.URL;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowvisorDevice 
{
    protected Logger logger = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    protected String uid;
    protected String passwd;
    protected String url;

    protected XmlRpcClientConfigImpl config;
    protected XmlRpcClient client;
    
    boolean connected = false;
    
    public FlowvisorDevice()
    {

    }

    public FlowvisorDevice(String url, String uid, String passwd)
    {
        this.url = url;
        this.uid = uid;
        this.passwd = passwd;
    }

    public FlowvisorDevice (String url)
    {
        this.url = url;
    }

    public void setUrl(String address, String port)
    {
        url = "https://" + address + ":" + port + "/xmlrpc";
    }

    public String getUrl()
    {
        return url;
    }

    public Object execute(String exec, Object[] param) throws CommandException
    {
        Object reply = null;
        try {
            reply = (Boolean) this.client.execute(exec, param);
        } catch (XmlRpcException e) {
            throw new CommandException(e.getMessage());
        }
        return reply;
    }

    public void connect() throws CommandException
    {
        installDumbTrust();
        config = new XmlRpcClientConfigImpl();
        config.setBasicUserName(uid);
        config.setBasicPassword(passwd);
        try {
            config.setServerURL(new URL(url));
        } catch (MalformedURLException e) {
            //e.printStackTrace();
            logger.error("FlowVisorDevice: unable to connect to " + url +
                         " user " + uid + " pass " + passwd +
                         " due to URL problem " + e.getMessage());
            throw new CommandException(e.getMessage());
        } catch (Exception e) {
            logger.error("FlowVisorDevice: unable to connect to " + url +
                         " user " + uid + " pass " + passwd +
                         " due to exception " + e.getMessage());
            throw new CommandException(e.getMessage());
        }

        config.setEnabledForExtensions(true);

        client = new XmlRpcClient();
        client.setConfig(config);
    }

    private TrustManager[] getTrustAllManager()
    {
        // Create a trust manager that does not validate certificate chains
        // System.err.println("WARN: blindly trusting server cert - FIXME");
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs,
                                            String authType) {
                // Trust always
            }

            public void checkServerTrusted(X509Certificate[] certs,
                                            String authType) {
                // Trust always
            }
        } };
        return trustAllCerts;
    }

    public void installDumbTrust()
    {
        TrustManager[] trustAllCerts = getTrustAllManager();
        try {
            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            // Create empty HostnameVerifier
            HostnameVerifier hv = new HostnameVerifier() {
                public boolean verify(String arg0, SSLSession arg1) {
                    return true;
                }
            };

            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(hv);
        } catch (KeyManagementException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public boolean isConnected()
    {
        return (connected);
    }
}

