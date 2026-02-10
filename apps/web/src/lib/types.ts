export type UserRole = "user" | "admin";

export interface AuthUser {
  id: string;
  userId?: string;
  email: string;
  name: string;
  role: UserRole;
}

export interface EventSummary {
  id: string;
  title: string;
  venue?: string;
  event_date?: string;
  status?: string;
  artist_name?: string;
  min_price?: number;
  max_price?: number;
  poster_image_url?: string;
  sale_start_date?: string;
  sale_end_date?: string;
}

export interface TicketType {
  id: string;
  event_id?: string;
  name: string;
  price: number;
  total_quantity: number;
  available_quantity: number;
  description?: string;
}

export interface Seat {
  id: string;
  event_id: string;
  section: string;
  row_number: number;
  seat_number: number;
  seat_label: string;
  price: number;
  status: "available" | "reserved" | "locked";
}

export interface SeatLayoutSection {
  name: string;
  rows: number;
  seatsPerRow: number;
  price: number;
  startRow: number;
}

export interface SeatLayout {
  id: string;
  name: string;
  description?: string;
  total_seats: number;
  layout_config: { sections: SeatLayoutSection[] };
}

export interface ReservationItem {
  id: string;
  seat_id?: string;
  ticket_type_id?: string;
  quantity: number;
  unit_price: number;
  subtotal: number;
  seat_info?: {
    section?: string;
    row_number?: number;
    seat_number?: number;
    seat_label?: string;
  };
}

export interface Reservation {
  id: string;
  user_id?: string;
  event_id?: string;
  reservation_number: string;
  total_amount: number;
  status: string;
  payment_method?: string;
  payment_status?: string;
  expires_at?: string;
  created_at?: string;
  updated_at?: string;
  items?: ReservationItem[];
  event?: EventSummary;
}

export interface NewsArticle {
  id: string;
  title: string;
  content: string;
  author: string;
  views?: number;
  is_pinned?: boolean;
  created_at?: string;
}

export interface QueueStatus {
  status?: "queued" | "active" | "not_in_queue";
  queued: boolean;
  position?: number;
  peopleAhead?: number;
  peopleBehind?: number;
  estimatedWait?: number;
  nextPoll?: number;
  currentUsers?: number;
  threshold?: number;
  queueSize?: number;
  eventInfo?: {
    title?: string;
    artist?: string;
  };
}

export type MembershipTier = "BRONZE" | "SILVER" | "GOLD" | "DIAMOND";

export interface Artist {
  id: string;
  name: string;
  image_url?: string;
  description?: string;
  membership_price?: number;
  event_count?: number;
  member_count?: number;
  created_at?: string;
}

export interface ArtistMembership {
  id: string;
  user_id: string;
  artist_id: string;
  artist_name?: string;
  artist_image_url?: string;
  tier: MembershipTier;
  effective_tier?: MembershipTier;
  points: number;
  status: string;
  joined_at?: string;
  expires_at?: string;
  membership_price?: number;
}

export interface MembershipPointLog {
  id: string;
  action_type: string;
  points: number;
  description?: string;
  created_at?: string;
}

export interface MembershipBenefits {
  tier: MembershipTier;
  preSalePhase: number | null;
  preSaleLabel: string;
  bookingFeeSurcharge: number;
  transferAccess: boolean;
  transferFeePercent: number | null;
  nextTierThreshold: number | null;
}

export interface TicketTransfer {
  id: string;
  reservation_id: string;
  seller_id: string;
  buyer_id?: string;
  artist_id: string;
  artist_name?: string;
  artist_image_url?: string;
  original_price: number;
  transfer_fee: number;
  transfer_fee_percent: number;
  total_price: number;
  status: string;
  created_at?: string;
  completed_at?: string;
  event_title?: string;
  event_date?: string;
  venue?: string;
  seats?: string;
}

export interface StatsOverview {
  totalEvents?: number;
  totalReservations?: number;
  totalRevenue?: number;
  totalUsers?: number;
  confirmedReservations?: number;
  cancelledReservations?: number;
  activeEvents?: number;
}

export interface DailyStats {
  date: string;
  reservations: number;
  revenue: number;
  cancellations?: number;
}

export interface EventStats {
  event_id: string;
  title: string;
  total_reservations: number;
  total_revenue: number;
  seat_utilization?: number;
}
