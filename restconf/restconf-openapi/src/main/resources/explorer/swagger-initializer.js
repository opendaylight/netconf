window.onload = function() {
    var xmlHttp = new XMLHttpRequest();
    xmlHttp.open("GET", document.URL.split('/apidoc')[0] + "/apidoc/openapi3/apis/mounts", false);
    xmlHttp.send( null );

    var base_url_rfc = document.URL.split('/apidoc')[0] + '/apidoc/openapi3/apis/mounts/';
    var swagger_urls = [{url: document.URL.split('/apidoc')[0] + "/apidoc/openapi3/apis/single", name: "Controller resources - RestConf RFC 8040"}];
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
        var container = document.createElement("DIV");
        var textNode = document.createElement("DIV");
        node.height = 40;
        node.src = 'logo_small.png';
        textNode.innerText = "OpenDaylight RestConf API Documentation";
        textNode.style="padding-top: 5px";
        node2.prepend(node);
        node2.append(textNode);
        topbar[0].children[0].remove();
        container.prepend(node2);
        topbar[0].prepend(container);
        node.style = "padding-right: 18px; float: left";
        topbar[0].children[0].style="color:white; display: contents;";
        topbar.style="display: flex; flex-direction: column !important";
        var formWrapper = document.getElementByClass('download-url-wrapper');
        formWrapper.style="display:flex;flex:3;justify-content:flex-start !important";

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
