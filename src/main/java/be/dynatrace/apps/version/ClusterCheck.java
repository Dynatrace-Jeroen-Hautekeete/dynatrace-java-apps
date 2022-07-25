package be.dynatrace.apps.version;

import java.io.FileInputStream;
import java.util.Properties;

import be.dynatrace.api.client.ApiClient;
import be.dynatrace.api.env.v1.ClusterTime;
import be.dynatrace.api.env.v1.ClusterVersion;

public class ClusterCheck {

	public static void main(String[] args) {

		Properties p=new Properties();
		try {
			p.load(new FileInputStream(args[0]+".properties"));
		} catch (Exception e) {
			System.err.println("Config file not found or invalid property file");
//			System.exit(-1);			
		}
		
		
		ApiClient ac=new ApiClient(p);
		
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
