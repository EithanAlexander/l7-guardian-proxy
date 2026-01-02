package com.l7guardian.proxy.infrastructure.forward;

import com.l7guardian.proxy.domain.model.ProxyRequest;
import com.l7guardian.proxy.domain.model.ProxyResponse;

public interface ProxyForwarder {

    /**
     * Forwards a validated proxy request to a backend and returns the response.
     *
     * @param request the immutable proxy request
     * @return the backend response
     * @throws ProxyForwardingException if forwarding fails
     */
    ProxyResponse forward(ProxyRequest request);
}
