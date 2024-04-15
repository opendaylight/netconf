import React from "react";
import { useState } from 'react';
import ReactDOM from "react-dom";
import SwaggerUI from "swagger-ui-react";

import "swagger-ui-react/swagger-ui.css";
import "./styles.css";

const ModulesStructureConverterPlugin = () => {
  return {
    statePlugins: {
      spec: {
        wrapSelectors: {
          taggedOperations: (ori) => (...args) => {
            return ori(...args)
              .filter(tagMeta => tagMeta.get("operations") && tagMeta.get("operations").size > 0)
          }
        }
      }
    }
  }
}

const defaultModelsExpandDepth = 1;
const defaultModelExpandDepth = 1;
const docExpansion = 'none';
const plugins = [ModulesStructureConverterPlugin];
const pages = [1, 2];
let currentPage = pages[0];
let url = 'http://localhost:8181/openapi/api/v3/single?offset=0&limit=20';

function App() {
  const setPage = (page) => {
    if (currentPage === page) return;

    currentPage = page;

    switch (page) {
      case 1:
        url = 'http://localhost:8181/openapi/api/v3/single?offset=0&limit=20';
      case 2:
        url = 'http://localhost:8181/openapi/api/v3/single?offset=20&limit=20';
    }

    setState({ page });
  }

  const [state, setState] = useState({ page: 1 });

  return (
    <div className="App">
      <SwaggerUI
        url={url}
        defaultModelsExpandDepth={defaultModelsExpandDepth}
        defaultModelExpandDepth={defaultModelExpandDepth}
        docExpansion={docExpansion}
        plugins={plugins}
      />
      <div class="pagination">
        <div className={currentPage === 2 ? 'txt-clr-accent-2' : ''} onClick={() => setPage(1)}>1</div>
        <div className={currentPage === 1 ? 'txt-clr-accent-2' : ''} onClick={() => setPage(2)}>2</div>
      </div>
    </div>
  );
}

const rootElement = document.getElementById("root");
ReactDOM.render(<App />, rootElement);
