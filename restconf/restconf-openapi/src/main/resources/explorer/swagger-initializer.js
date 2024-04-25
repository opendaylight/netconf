window.onload = function () {
  var depthValue = localStorage.getItem('depth') || '3';
  var widthValue = localStorage.getItem('width') || '5';
  const queryParams = `?depth=${depthValue}&width=${widthValue}`;
  var xmlHttp = new XMLHttpRequest();
  xmlHttp.open('GET', document.URL.split('/openapi')[0] + `/openapi/api/v3/mounts/`, false);
  xmlHttp.send(null);

  var base_url_rfc = document.URL.split('/openapi')[0] + `/openapi/api/v3/mounts/`;
  var swagger_urls = [{ url: document.URL.split('/openapi')[0] + `/openapi/api/v3/single${queryParams}`, name: 'Controller resources - RestConf RFC 8040' }];
  var devices = JSON.parse(xmlHttp.responseText);
  for (var i = 0; i < devices.length; i++) {
    var device_name = devices[i]['instance'].split('=')[2].replace('/', '');
    var device_id = devices[i]['id'];
    var dict = {
      url: base_url_rfc + device_id + queryParams,
      name: device_name + ' resources - RestConf RFC 8040',
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
    onComplete: function (swaggerApi, swaggerUi) {
      var wrappers = document.getElementsByClassName('wrapper');
      var topbar = document.getElementsByClassName('topbar-wrapper');
      var node2 = document.createElement('h2');
      var node = document.createElement('img');
      node.height = 40;
      node.src = 'logo_small.png';
      node2.innerText = 'OpenDaylight RestConf API Documentation';
      node2.prepend(node);
      topbar[0].children[0].remove();
      topbar[0].prepend(node2);
      node.style = 'padding-right: 18px;';
      topbar[0].children[0].style = 'color:white;';

      const serversContainer = document.getElementsByClassName('schemes-server-container')[0];

      const selectDepthWrapper = document.createElement('div');
      selectDepthWrapper.classList.add('input-wrapper');
      var selectDepthLabel = document.createElement('label');
      selectDepthLabel.innerText = 'Depth';

      const depthInput = document.createElement('input');
      depthInput.type = 'number';
      depthInput.max = '20';
      depthInput.min = '0';
      depthInput.value = depthValue;
      depthInput.addEventListener('change', (event) => {
        localStorage.setItem('depth', event.target.value);
        window.location.reload();
      });
      selectDepthWrapper.appendChild(selectDepthLabel);
      selectDepthWrapper.appendChild(depthInput);
      serversContainer.appendChild(selectDepthWrapper);

      const selectWidthWrapper = document.createElement('div');
      selectWidthWrapper.classList.add('input-wrapper');
      var selectWidthLabel = document.createElement('label');
      selectWidthLabel.innerText = 'Width';

      const widthInput = document.createElement('input');
      widthInput.type = 'number';
      widthInput.max = '20';
      widthInput.min = '0';
      widthInput.value = widthValue;
      widthInput.addEventListener('change', (event) => {
        localStorage.setItem('width', event.target.value);
        window.location.reload();
      });
      selectWidthWrapper.appendChild(selectWidthLabel);
      selectWidthWrapper.appendChild(widthInput);
      serversContainer.appendChild(selectWidthWrapper);

      var modules = document.getElementsByClassName('opblock-tag-section');
      for (var i = 0; i < modules.length; i++) {
        var innerText = modules[i].getElementsByTagName('a')[0].innerText;
        var arrayInnerText = innerText.split(' ');
        if (arrayInnerText.length > 0) {
          modules[i].getElementsByTagName('a')[0].innerText = arrayInnerText[arrayInnerText.length - 1];
        }
      }
      document.getElementsByClassName('select-label')[0].style = 'max-width: max-content; padding-left: 20px';
      document.getElementById('select').style = 'flex: none; width: auto';
    },
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
    plugins: [
      SwaggerUIBundle.plugins.DownloadUrl
    ],
    layout: 'StandaloneLayout'
  });

  window.ui = ui;
}
