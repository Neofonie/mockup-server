# mockup-server

## **⚠️ THIS PROJECT IS NOW UNMAINTED AND IN READ-ONLY MODE ⚠️**

simple mockup-server

Wofür?
	Kann genutzt werden um Serverantworten zu simulieren für den Fall das ein
        tatsächlicher Server nicht direkt verfügbar/erreichbar ist (bspw. 
        RESTful-Schnittstellen, Resourcen, etc.)



Wie?
	Über einen Proxy (bspw. FoxyProxy für Browser, oder andere Lösung wenn
        Systemweit) können URL-Aufrufe an diesen Server gerichtet werden. Dazu
        muß der Server über CLI (oder gegebener CMD-Start-Datei) gestartet werden.
        Per default ist der Server dann über http://localhost:9090 bzw.
        http://127.0.0.1:9090 erreichbar.
	Mit der 'response.scheme'-Datei kann konfiguriert werden bei welchen URL's
        der Server mit welchen Inhalten antwortet.
	Bei Änderungen an der 'response.scheme'-Datei zur Laufzeit wird diese Datei
        direkt neu in den laufenden Server eingelesen, d.h. ein Neustart entfällt
        und man kann verschiedene Situationen direkt testen in dem die Antworten
        modifiziert werden.



Response Scheme
        Ein Response wird über einen 'Block' gepflegt, gekennzeichnet mit '{{' am
        Anfang und '}}' am Ende. Beide müssen allein in einer eigenen Zeile stehen.
        Der Typ eines Response wird direkt nach '{{' angegeben, aktuell werden drei
        Type unterstützt:
        {{HTML      gibt einen HTML-Content zurück
        {{JSON      gibt einen JSON-Content zurück
        {{FILE      gibt eine Datei zurück
        Nachdem ein Block geöffnet wurde muß danach die URL angegeben werden auf
        die reagiert werden soll. Die URL wird dabei als RegEx angegeben und muß
        auf einer eigenen Zeile stehen.
        Alles was dann folgt ist der Content der zurückgegeben wird. Im Falle von
        HTML oder JSON ist es entsprechener HTML/JSON-Code, bei FILE wird der relative
        oder absolute Dateipfad zu der Datei angegeben.

	URL's werden RegEx-Muster angegeben, es können auch Gruppen verwendet werden
	welche wiederum in einem String-Content (JSON/HTML) wiederverwendet werden
	können
	
	Bsp.: die Gruppe '(\d*)' (Gruppe 1) wird an Stelle von '{1}' gesetzt. Es
		gilt die Indizierung von Gruppen in Matchern.
	
	https?:\/\/[^\/]*\/.foo/bar/p/(\d*)/.*
    
    	<div>Produkt {1}</div>
