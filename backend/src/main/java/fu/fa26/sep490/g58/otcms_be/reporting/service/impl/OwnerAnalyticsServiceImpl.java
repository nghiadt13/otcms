package fu.fa26.sep490.g58.otcms_be.reporting.service.impl;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerAnalyticsDtos.DailyRevenue;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerAnalyticsDtos.DoctorRow;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerAnalyticsDtos.Funnel;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerAnalyticsDtos.Metric;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerAnalyticsDtos.OwnerAnalyticsResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerAnalyticsDtos.Period;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerAnalyticsDtos.Revenue;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerAnalyticsDtos.ServiceRow;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.OwnerAnalyticsRepository;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.OwnerAnalyticsRepository.AppointmentFunnel;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.OwnerAnalyticsRepository.DailyAmount;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.OwnerAnalyticsRepository.DoctorActivity;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.OwnerAnalyticsRepository.DoctorAmount;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.OwnerAnalyticsRepository.ServiceAmount;
import fu.fa26.sep490.g58.otcms_be.reporting.service.OwnerAnalyticsService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OwnerAnalyticsServiceImpl implements OwnerAnalyticsService {
    private final OwnerAnalyticsRepository repository;

    public OwnerAnalyticsServiceImpl(OwnerAnalyticsRepository repository) {
        this.repository = repository;
    }

    @Override
    public OwnerAnalyticsResponse analytics(ReportingPeriod period) {
        ReportingPeriod previous = period.previous();
        AppointmentFunnel funnel = repository.appointmentFunnel(period);
        AppointmentFunnel funnelBefore = repository.appointmentFunnel(previous);
        return new OwnerAnalyticsResponse(period(period), period(previous), revenue(period, previous),
            new Metric(funnel.booked(), funnelBefore.booked()),
            new Metric(repository.arrivals(period), repository.arrivals(previous)),
            new Funnel(funnel.booked(), funnel.arrived(), funnel.completed(), funnel.cancelled(), funnel.noShow()),
            doctors(period, previous), services(period, previous));
    }

    private static Period period(ReportingPeriod p) {
        return new Period(p.from().toString(), p.to().toString());
    }

    /** One query covers both periods; the previous period is lined up with the current one day by day. */
    private Revenue revenue(ReportingPeriod period, ReportingPeriod previous) {
        Map<String, Long> cash = new HashMap<>(), payos = new HashMap<>();
        for (DailyAmount row : repository.dailyRevenue(previous.start(), period.until())) {
            ("CASH".equals(row.method()) ? cash : payos).merge(row.date(), row.amount(), Long::sum);
        }
        List<DailyRevenue> daily = new ArrayList<>();
        long cashNow = 0, payosNow = 0, cashBefore = 0, payosBefore = 0;
        for (LocalDate day = period.from(), before = previous.from(); !day.isAfter(period.to()); day = day.plusDays(1), before = before.plusDays(1)) {
            long dayCash = cash.getOrDefault(day.toString(), 0L), dayPayos = payos.getOrDefault(day.toString(), 0L);
            long beforeCash = cash.getOrDefault(before.toString(), 0L), beforePayos = payos.getOrDefault(before.toString(), 0L);
            daily.add(new DailyRevenue(day.toString(), dayCash, dayPayos, beforeCash + beforePayos));
            cashNow += dayCash; payosNow += dayPayos; cashBefore += beforeCash; payosBefore += beforePayos;
        }
        return new Revenue(new Metric(cashNow + payosNow, cashBefore + payosBefore), new Metric(cashNow, cashBefore),
            new Metric(payosNow, payosBefore), daily);
    }

    /** Doctors with money or finished visits in the period, then the untraceable money last. */
    private List<DoctorRow> doctors(ReportingPeriod period, ReportingPeriod previous) {
        List<DoctorAmount> earlier = repository.doctorRevenue(previous);
        Map<UUID, Long> before = earlier.stream().filter(row -> row.doctorId() != null)
            .collect(Collectors.toMap(DoctorAmount::doctorId, DoctorAmount::amount));
        long unknownBefore = earlier.stream().filter(row -> row.doctorId() == null).mapToLong(DoctorAmount::amount).sum();
        Map<UUID, DoctorActivity> activity = repository.doctorActivity(period).stream()
            .collect(Collectors.toMap(DoctorActivity::doctorId, row -> row));
        Map<UUID, DoctorRow> rows = new LinkedHashMap<>();
        long unknown = 0;
        for (DoctorAmount money : repository.doctorRevenue(period)) {
            if (money.doctorId() == null) { unknown += money.amount(); continue; }
            DoctorActivity done = activity.get(money.doctorId());
            rows.put(money.doctorId(), new DoctorRow(money.doctorId().toString(), money.name(), done == null ? 0 : done.visits(),
                done == null ? 0 : done.activeDays(), money.amount(), before.getOrDefault(money.doctorId(), 0L)));
        }
        for (DoctorActivity done : activity.values()) {
            rows.putIfAbsent(done.doctorId(), new DoctorRow(done.doctorId().toString(), done.name(), done.visits(), done.activeDays(), 0,
                before.getOrDefault(done.doctorId(), 0L)));
        }
        List<DoctorRow> result = rows.values().stream()
            .sorted(Comparator.comparingLong(DoctorRow::revenue).reversed().thenComparing(row -> Objects.toString(row.name(), ""))).collect(Collectors.toCollection(ArrayList::new));
        if (unknown > 0) result.add(new DoctorRow(null, "Không xác định", 0, 0, unknown, unknownBefore));
        return result;
    }

    private List<ServiceRow> services(ReportingPeriod period, ReportingPeriod previous) {
        Map<UUID, Long> before = repository.serviceRevenue(previous).stream()
            .collect(Collectors.toMap(ServiceAmount::serviceId, ServiceAmount::amount));
        return repository.serviceRevenue(period).stream().map(row -> new ServiceRow(row.serviceId().toString(), row.name(),
            row.category(), row.count(), row.amount(), before.getOrDefault(row.serviceId(), 0L))).toList();
    }
}
