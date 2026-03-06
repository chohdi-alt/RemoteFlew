package tn.pi.remoteflowapplication.infrastructure.persistence.projection;

import tn.pi.remoteflowapplication.domain.state.RequestStatus;

public interface StatusCountProjection {
    RequestStatus getStatus();

    long getCount();
}
