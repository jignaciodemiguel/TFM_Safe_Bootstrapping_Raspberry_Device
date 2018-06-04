package es.naxo.tfm.utils;

import com.amazonaws.services.iot.client.AWSIotException;

public class Log {

	public static void escribirLog (String texto)    {
	
		System.err.println (texto);
	}
	
	public static void escribirLogPuntos (String texto)    {
		
		System.err.print(texto);
		int i = 0; 
		
        while (i < 0) {

            System.err.print(".");

            try {
                Thread.sleep(200);
            } 
            catch (InterruptedException e) {
                System.err.println("Error al escribir el log");
                return;
            }
            
            i++;
        }
        
        System.err.println("");
	}

	public static void escribirLogPuntosSinLinea (String texto)    {
		
		System.err.print(texto);
		int i = 0; 
		
        while (i < 5) {

            System.err.print(".");

            try {
                Thread.sleep(200);
            } 
            catch (InterruptedException e) {
                System.err.println("Error al escribir el log");
                return;
            }
            
            i++;
        }
	}
}
