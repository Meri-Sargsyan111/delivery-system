package com.example.trackingservice.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RouteProjectorTest {

    /** A straight 5-point line running north, ~11.1km per 0.1 degree of latitude. */
    private static final List<double[]> STRAIGHT_ROUTE = List.of(
            new double[]{40.0000, 44.0000},
            new double[]{40.1000, 44.0000},
            new double[]{40.2000, 44.0000},
            new double[]{40.3000, 44.0000},
            new double[]{40.4000, 44.0000}
    );

    @Test
    void cumulativeDistanceKm_firstPointIsZero_lastPointIsTotalRouteLength() {
        double[] cumulative = RouteProjector.cumulativeDistanceKm(STRAIGHT_ROUTE);

        assertThat(cumulative[0]).isEqualTo(0.0);
        assertThat(cumulative[cumulative.length - 1]).isGreaterThan(40);
    }

    @Test
    void project_atRouteStart_reportsZeroProgressAndFullRemainingDistance() {
        double[] cumulative = RouteProjector.cumulativeDistanceKm(STRAIGHT_ROUTE);

        RouteProjector.ProjectionResult result =
                RouteProjector.project(STRAIGHT_ROUTE, cumulative, 0, 40.0000, 44.0000, 200);

        assertThat(result.matchedIndex()).isEqualTo(0);
        assertThat(result.routeProgressPercent()).isCloseTo(0, within(0.5));
        assertThat(result.remainingDistanceKm()).isCloseTo(cumulative[cumulative.length - 1], within(0.5));
        assertThat(result.deviated()).isFalse();
    }

    @Test
    void project_atRouteEnd_reportsFullProgressAndZeroRemainingDistance() {
        double[] cumulative = RouteProjector.cumulativeDistanceKm(STRAIGHT_ROUTE);

        RouteProjector.ProjectionResult result =
                RouteProjector.project(STRAIGHT_ROUTE, cumulative, 0, 40.4000, 44.0000, 200);

        assertThat(result.matchedIndex()).isEqualTo(STRAIGHT_ROUTE.size() - 1);
        assertThat(result.routeProgressPercent()).isCloseTo(100, within(0.5));
        assertThat(result.remainingDistanceKm()).isCloseTo(0, within(0.5));
    }

    @Test
    void project_halfway_reportsApproximatelyHalfProgress() {
        double[] cumulative = RouteProjector.cumulativeDistanceKm(STRAIGHT_ROUTE);

        RouteProjector.ProjectionResult result =
                RouteProjector.project(STRAIGHT_ROUTE, cumulative, 0, 40.2000, 44.0000, 200);

        assertThat(result.routeProgressPercent()).isCloseTo(50, within(1.0));
    }

    @Test
    void project_neverRegressesBehindLastMatchedIndex_evenIfCourierJittersBackward() {
        double[] cumulative = RouteProjector.cumulativeDistanceKm(STRAIGHT_ROUTE);

        RouteProjector.ProjectionResult result =
                RouteProjector.project(STRAIGHT_ROUTE, cumulative, 3, 40.0100, 44.0000, 200);

        assertThat(result.matchedIndex()).isEqualTo(3);
    }

    @Test
    void project_farFromRoute_flagsDeviated() {
        double[] cumulative = RouteProjector.cumulativeDistanceKm(STRAIGHT_ROUTE);

        RouteProjector.ProjectionResult result =
                RouteProjector.project(STRAIGHT_ROUTE, cumulative, 0, 40.1000, 44.0100, 200);

        assertThat(result.deviated()).isTrue();
    }

    @Test
    void project_onRoute_doesNotFlagDeviated() {
        double[] cumulative = RouteProjector.cumulativeDistanceKm(STRAIGHT_ROUTE);

        RouteProjector.ProjectionResult result =
                RouteProjector.project(STRAIGHT_ROUTE, cumulative, 0, 40.1000, 44.0000, 200);

        assertThat(result.deviated()).isFalse();
    }
}
