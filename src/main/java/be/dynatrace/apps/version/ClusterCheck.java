package be.dynatrace.apps.version;

import java.io.FileInputStream;
import java.util.Properties;

import be.dynatrace.api.client.ApiClient;
import be.dynatrace.api.env.v1.ClusterTime;
import be.dynatrace.api.env.v1.ClusterVersion;

public class ClusterCheck {

	public static void main(String[] args) {

		ApiClient ac=null;
		try {
			Properties p=new Properties();
			p.load(new FileInputStream("config\\"+args[0]+".properties"));
			ac=new ApiClient(p);
		} catch (Exception e) {
			System.err.println("Config file not found or invalid property file");
			System.exit(-1);			
		}
		
		
		
		try {
			System.out.println("Getting time");
			System.out.println(ClusterTime.getTime(ac));
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
		try {
			System.out.println("Getting version");
			System.out.println(ClusterVersion.getClusterVersion(ac));
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
		
	}

}
