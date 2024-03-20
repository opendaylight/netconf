/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
import React from "react";
import ReactDOM from "react-dom";
import SwaggerUI from "swagger-ui-react";

import "swagger-ui-react/swagger-ui.css";

const defaultModelsExpandDepth = 1;
const defaultModelExpandDepth = 1;

function App() {
  return (
    <div className="App">
      <SwaggerUI
        url="https://petstore3.swagger.io/api/v3/openapi.json"
        defaultModelsExpandDepth={defaultModelsExpandDepth}
        defaultModelExpandDepth={defaultModelExpandDepth}
      />
    </div>
  );
}

const rootElement = document.getElementById("root");
ReactDOM.render(<App />, rootElement);
