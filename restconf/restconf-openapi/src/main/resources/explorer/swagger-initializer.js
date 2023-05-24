window.onload = function() {
    var xmlHttp = new XMLHttpRequest();
    xmlHttp.open("GET", document.URL.split('/openapi')[0] + "/openapi/api/v3/mounts", false);
    xmlHttp.send( null );

    var base_url_rfc = document.URL.split('/openapi')[0] + '/openapi/api/v3/mounts/';
    var swagger_urls = [{url: document.URL.split('/openapi')[0] + "/openapi/api/v3/single", name: "Controller resources - RestConf RFC 8040"}];
    var devices = JSON.parse(xmlHttp.responseText);
    for (var i =0; i < devices.length; i++) {
      var device_name = devices[i]['instance'].split('=')[2].replace('/', '');
      var dveice_id = devices[i]['id'];
      var dict = {
        url: base_url_rfc + dveice_id,
        name: device_name + " resources - RestConf RFC 8040",
      };
      swagger_urls.push(dict);
    }

    // Begin Swagger UI call region
    const ui = SwaggerUIBundle({
      urls: swagger_urls,
      dom_id: '#swagger-ui',
      docExpansion: 'none',
      deepLinking: true,
      showAlternativeSchemaExample: true,
      onComplete: function(swaggerApi, swaggerUi){
        var wrappers = document.getElementsByClassName('wrapper');
        var topbar = document.getElementsByClassName('topbar-wrapper');
        var node2 = document.createElement("H2");
        var node = document.createElement("IMG");
        node.height = 40;
        node.src = 'logo_small.png';
        node2.innerText = "OpenDaylight RestConf API Documentation";
        node2.prepend(node);
        topbar[0].children[0].remove();
        topbar[0].prepend(node2);
        node.style = "padding-right: 18px;";
        topbar[0].children[0].style="color:white;";

        var modules = document.getElementsByClassName('opblock-tag-section')
        for(var i = 0; i < modules.length; i++) {
          var innerText = modules[i].getElementsByTagName('a')[0].innerText;
          var arrayInnerText = innerText.split(' ')
          if (arrayInnerText.length > 0) {
            modules[i].getElementsByTagName('a')[0].innerText = arrayInnerText[arrayInnerText.length - 1];
          }
        }
        document.getElementsByClassName("select-label")[0].style = "max-width: max-content; padding-left: 20px";
        document.getElementById("select").style = "flex: none; width: auto"
      },
      presets: [
        SwaggerUIBundle.presets.apis,
        SwaggerUIStandalonePreset
      ],
      plugins: [
        SwaggerUIBundle.plugins.DownloadUrl
      ],
      layout: "StandaloneLayout"
    });

    window.ui = ui;
}
