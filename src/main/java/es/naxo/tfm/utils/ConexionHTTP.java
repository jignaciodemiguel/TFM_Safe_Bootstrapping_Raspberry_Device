package es.naxo.tfm.utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

public class ConexionHTTP {

    private final static String MethodDominio = "https://";   
    private final static String URLDominio = "backend.look4family.com";    
    private final static String URLConexionProduccion = MethodDominio + URLDominio + "/TFM_Safe_Bootstrapping_Raspberry_Server/";  // Desde el emulador.
    private final static String URLConexionDesarrollo = "http://localhost:8081/TFM_Safe_Bootstrapping_Raspberry_Server/";  // Desde el emulador.

    public final static String GET = "GET";
    public final static String POST = "POST";
    private final static String KO_EXCEPTION = "KO";

    public static String peticion (String URLServicio, String method, HashMap<String, String> parametros) {
        return peticion(URLServicio, method, parametros, false);
    }

    public static String peticion (String URLServicio, String method, HashMap<String, String> parametros, boolean desarrollo) {

        HttpURLConnection con = null;
        StringBuffer cadena = new StringBuffer();

        try {

            if (parametros != null) {
                Collection collection = parametros.keySet();
                Iterator<String> iterador = collection.iterator();

                while (iterador.hasNext()) {
                    String clave = iterador.next();
                    String valor = parametros.get(clave);
                    if (valor != null && !valor.equals("")) {
                        cadena.append(clave).append("=").append(URLEncoder.encode(valor, "UTF-8"));

                        // Si no es el ultimo, le meto el &
                        if (iterador.hasNext()) {
                            cadena.append("&");
                        }
                    }
                }
            }

            int tamanyo = cadena.toString().getBytes().length;
            if (desarrollo == true) {
                URLServicio = URLConexionDesarrollo + URLServicio;
            }
            else   {
                URLServicio = URLConexionProduccion + URLServicio;
            }

            // Activar metodo POST
            if (GET.equals(method)) {

                // Genero la conexion GET
                URLServicio += "?" + cadena;
                URL url = new URL(URLServicio);
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");

                //con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("Accept", "application/json");
            }

            else {

                // Genero la conexion POST
                URL url = new URL(URLServicio);
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Length", new Integer(tamanyo).toString());
                con.setRequestProperty("charset", "UTF-8");

                //con.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                con.setRequestProperty("Accept", "application/json");

                con.setDoInput(true);
                con.setDoOutput(true);
                con.setUseCaches(false);

                //Send request
                DataOutputStream wr = new DataOutputStream(con.getOutputStream());
                wr.writeBytes(cadena.toString());
                wr.flush();
                wr.close();
            }

            // Obtener el estado del recurso
            int statusCode = con.getResponseCode();
            if (statusCode != 200) {
                System.err.println ("Error al ejecutar el servicio" + URLServicio + " StatusCode: " + statusCode);
                return KO_EXCEPTION;
            }
            
            else {

                //Get Response
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String line;
                StringBuffer response = new StringBuffer();
                while ((line = in.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }

                // Borro el ultimo caracter '\r';
                response.deleteCharAt(response.length() - 1);
                in.close();

                //Log.e(TAG, "La respuesta (" + (System.currentTimeMillis() - tiempo) + ") al servicio " + URLServicio + " es buena "); // + response);
                return response.toString();
            }
        }

        catch (MalformedURLException ex) {
        	System.err.println ("MalformedURLException al crear la URL para el servicio: " + URLServicio + " ");
        	ex.printStackTrace();
            return KO_EXCEPTION;
        }

        catch (IOException ex) {
        	System.err.println ("IOExcepcion al contactar con la URL del servicio: " + URLServicio + " ");
        	ex.printStackTrace();
            return KO_EXCEPTION;
        }

        catch (Exception ex) {
        	System.err.println ("Excepcion generica al conectar con el servicio " + URLServicio + " ");
        	ex.printStackTrace();
        	return KO_EXCEPTION;
        }

        finally {
            if (con != null) con.disconnect();
        }
    }
}



