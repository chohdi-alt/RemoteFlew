package tn.pi.remoteflowapplication.infrastructure.persistence.projection;

public interface MonthlyCountProjection {
    int getYear();

    int getMonth();

    long getCount();
}
