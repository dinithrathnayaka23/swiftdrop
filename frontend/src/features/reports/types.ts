/** Totals and rates for a date range (com.swiftdrop.reporting.dto.response.ReportSummary). */
export interface ReportSummary {
  /** First day, yyyy-MM-dd (inclusive). */
  from: string;
  /** Last day, yyyy-MM-dd (inclusive). */
  to: string;
  /** Parcels created in the range. */
  total: number;
  /** Parcels delivered in the range. */
  delivered: number;
  /** Parcels that failed in the range. */
  failed: number;
  /** delivered / (delivered + failed), as a percentage with two decimals. */
  successRate: number;
  /** failed / (delivered + failed), as a percentage with two decimals. */
  failureRate: number;
}

/** One day of the "deliveries per day" report (DailyDeliveryStat). */
export interface DailyDeliveryStat {
  /** yyyy-MM-dd */
  date: string;
  total: number;
  delivered: number;
  failed: number;
}

/** An inclusive range of days, both as yyyy-MM-dd. */
export interface DateRange {
  from: string;
  to: string;
}
