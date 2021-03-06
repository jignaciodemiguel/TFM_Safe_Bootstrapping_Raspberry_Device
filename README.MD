# TFM_Safe_Bootstrapping_Raspberry_Device

## Introducci�n

Este proyecto contiene el c�digo a ejecutar en el dispositivo Raspberry para realizar el proceso de bootstrapping seguro. Se ha elaborado dentro del Trabajo Fin de Master (TFM) dentro del Master en Ciberseguridad de la UCAM (2018), y es complemento del proyecto hermano 'TFM_Safe_Bootstrapping_Raspberry_Server' 

El bootstrapping es el proceso por el que un dispositivo IoT se registra por primera vez, de manera segura, contra la plataforma IoT contra la que posteriormente se comunicar�. Para realizar ese registro, debemos identificar de manera inequ�voca el dispositio, bien con un certificado digital de cliente, o bien con alg�n secreto compartido entre dispositivo y plataforma. Normalmente se suelen utilizan dos t�cnicas: 

+ Personalizar el firmware del dispositivo de manera que todos los firmwares sean distintos
+ Agregarle, a posteriori a la fabricaci�n, el secreto o certificado. 

Ambas t�cnicas tienen sus complicaciones, pues obliga a llevar una pol�tica segura de gesti�n de los secretos en el proceso de fabricaci�n, y aumenta el coste del mismo. 

El objetivo del proyecto es, usando un firmware igual entre todos los dispositivos, y sin introducirle ning�n secreto o certificado a posteriori en la fabricaci�n, que el dispositivo sea capaz de autoregistrarse, y conseguir su propio certificado cliente, distinto para cada dispositivo, con el que conectar de manera segura contra la plataforma IoT. 

El proyecto se ha desarrollado para su validaci�n en un dispositivo Raspberry PI 2. Se ha simulado que es un dispositivo IoT de gesti�n de la temperatura, ya que a partir de un sensor (te�rico) enviar� de manera peri�dica la temperatura para su registro dentro de la plataforma. 

## Instalaci�n

Para la instalaci�n, se deber� clonar en local el proyecto con GIT.

Una vez descargado a local, deberemos instalar el certificado raiz del servidor de la Plataforma IoT, que en algunas distribuciones de Raspbian no viene incluido. 

Para ello debemos localizar cual es el almacen de certificados (cacert) de nuestro JDK del dispositivo, y agregarle el certificado raiz, que se encuentra en el directorio "ks" del proyecto: 

```
sudo keytool -importcert -keystore /usr/lib/jvm/jdk-8-oracle-arm32-vfp-hflt/jre/lib/security/cacerts -v -file certificadoRaizDST.pem -alias 'dst_root_ca_x3'
```
En nuestro caso, sustituiremos la ruta del fichero cacerts por la correcta en cada dispositivo. Si nos devuelve un error de que ya est� instalado ese certificado, continuamos con la ejecuci�n puesto que ya viene instalado en la distribuci�n que tengamos. 

Ahora ya solo nos falta lanzar la instalaci�n con maven: 
```
mvn install
```
Y en la carpeta target, tendremos el jar correspondiente. 
```
cd target
```
Debemos utilizar el jar TFM_Safe_Bootstrapping_Raspberry_Device-full.jar, que incluye todas las dependencias externas para que su ejecuci�n sea lo m�s sencilla posible. 

## Modo de uso

Lo ejecutaremos de esta forma:
```
java -jar target\TFM_Safe_Bootstrapping_Raspberry_Device-full.jar
```
Y disponemos de las siguientes opciones para lanzarlo: 
```
--exec    Ejecuta el proceso completo de envio continuo de temperatura
--boot    Ejecuta el proceso de bootstrapping, para dejar el dispositivo preparado para la comunicaci�n
--reset   Resetea la configuraci�n para dejar el dispositivo con la configuraci�n inicial
--conf    Valida la configuraci�n para comprobar si est� preparado para iniciar la comunicaci�n de la temperatura
```
