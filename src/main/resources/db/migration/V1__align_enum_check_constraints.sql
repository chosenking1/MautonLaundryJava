-- Re-aligns CHECK constraints on every @Enumerated(EnumType.STRING) column
-- with the current enum values. Hibernate's ddl-auto=update does not amend
-- CHECK constraints when an enum gains values, which silently breaks writes
-- (see delivery_assignments.status incident, 2026-04-28).
--
-- Each block: drop the prior constraint if it exists, then add one named
-- <table>_<column>_check matching what Hibernate would generate. Idempotent.

-- bookings.booking_type → BookingType
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_booking_type_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_booking_type_check
    CHECK (booking_type IN ('LAUNDRY','CLEANING','SERVICE'));

-- bookings.status → BookingStatus
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check
    CHECK (status IN (
        'CREATED',
        'LAUNDRY_ASSIGNMENT_PENDING','LAUNDRY_ACCEPTED',
        'PICKUP_DISPATCH_PENDING','PICKUP_AGENT_ASSIGNED',
        'PICKED_UP','AT_LAUNDRY','WASHING','READY_FOR_DELIVERY',
        'DELIVERY_DISPATCH_PENDING','DELIVERY_AGENT_ASSIGNED',
        'OUT_FOR_DELIVERY','DELIVERED','COMPLETED','CANCELLED'
    ));

-- payment.payment_method → PaymentMethod
ALTER TABLE payment DROP CONSTRAINT IF EXISTS payment_payment_method_check;
ALTER TABLE payment ADD CONSTRAINT payment_payment_method_check
    CHECK (payment_method IN ('CARD','TRANSFER'));

-- payment.provider → PaymentProvider
ALTER TABLE payment DROP CONSTRAINT IF EXISTS payment_provider_check;
ALTER TABLE payment ADD CONSTRAINT payment_provider_check
    CHECK (provider IS NULL OR provider IN ('MONNIFY','SQUAD','PAYSTACK'));

-- payment.status → PaymentStatus
ALTER TABLE payment DROP CONSTRAINT IF EXISTS payment_status_check;
ALTER TABLE payment ADD CONSTRAINT payment_status_check
    CHECK (status IN ('PENDING','COMPLETED','FAILED','REFUNDED'));

-- delivery_assignments.status → DeliveryAssignmentStatus
ALTER TABLE delivery_assignments DROP CONSTRAINT IF EXISTS delivery_assignments_status_check;
ALTER TABLE delivery_assignments ADD CONSTRAINT delivery_assignments_status_check
    CHECK (status IN (
        'OFFERED','ACCEPTED','DECLINED',
        'ENROUTE_TO_CUSTOMER','ARRIVED_AT_CUSTOMER','PICKED_UP_FROM_CUSTOMER',
        'ENROUTE_TO_LAUNDRY','ARRIVED_AT_LAUNDRY','DELIVERED_TO_LAUNDRY',
        'PICKED_UP_FROM_LAUNDRY','ENROUTE_FROM_LAUNDRY_TO_CUSTOMER',
        'ARRIVED_AT_CUSTOMER_FOR_DELIVERY','DELIVERED_TO_CUSTOMER',
        'COMPLETED','CANCELLED'
    ));

-- delivery_assignments.phase → DeliveryAssignmentPhase
ALTER TABLE delivery_assignments DROP CONSTRAINT IF EXISTS delivery_assignments_phase_check;
ALTER TABLE delivery_assignments ADD CONSTRAINT delivery_assignments_phase_check
    CHECK (phase IN ('PICKUP_FROM_CUSTOMER','RETURN_TO_CUSTOMER'));

-- laundryman_assignments.status → LaundrymanAssignmentStatus
ALTER TABLE laundryman_assignments DROP CONSTRAINT IF EXISTS laundryman_assignments_status_check;
ALTER TABLE laundryman_assignments ADD CONSTRAINT laundryman_assignments_status_check
    CHECK (status IN ('OFFERED','ACCEPTED','REJECTED','AUTO_ACCEPTED','EXPIRED'));

-- agent_applications.type → AgentApplicationType
ALTER TABLE agent_applications DROP CONSTRAINT IF EXISTS agent_applications_type_check;
ALTER TABLE agent_applications ADD CONSTRAINT agent_applications_type_check
    CHECK (type IN ('LAUNDRY_AGENT','DELIVERY_AGENT'));

-- agent_applications.status → AgentApplicationStatus
ALTER TABLE agent_applications DROP CONSTRAINT IF EXISTS agent_applications_status_check;
ALTER TABLE agent_applications ADD CONSTRAINT agent_applications_status_check
    CHECK (status IN ('SUBMITTED','ROLE_CHANGE_REQUESTED','APPROVED','REJECTED'));

-- agent_applications.inspection_status → InspectionStatus
ALTER TABLE agent_applications DROP CONSTRAINT IF EXISTS agent_applications_inspection_status_check;
ALTER TABLE agent_applications ADD CONSTRAINT agent_applications_inspection_status_check
    CHECK (inspection_status IN ('NOT_REQUIRED','PENDING','PASSED','FAILED'));

-- discount_user_assignments.approval_status → ApprovalStatus
ALTER TABLE discount_user_assignments DROP CONSTRAINT IF EXISTS discount_user_assignments_approval_status_check;
ALTER TABLE discount_user_assignments ADD CONSTRAINT discount_user_assignments_approval_status_check
    CHECK (approval_status IN ('PENDING','APPROVED','REJECTED','REVOKED'));

-- verification_tokens.token_type → VerificationToken.TokenType
ALTER TABLE verification_tokens DROP CONSTRAINT IF EXISTS verification_tokens_token_type_check;
ALTER TABLE verification_tokens ADD CONSTRAINT verification_tokens_token_type_check
    CHECK (token_type IS NULL OR token_type IN ('EMAIL_VERIFICATION','PASSWORD_RESET'));

-- role_change_requests.status → RequestStatus
ALTER TABLE role_change_requests DROP CONSTRAINT IF EXISTS role_change_requests_status_check;
ALTER TABLE role_change_requests ADD CONSTRAINT role_change_requests_status_check
    CHECK (status IN ('PENDING','APPROVED','REJECTED'));

-- discounts.owner_type → OwnerType
ALTER TABLE discounts DROP CONSTRAINT IF EXISTS discounts_owner_type_check;
ALTER TABLE discounts ADD CONSTRAINT discounts_owner_type_check
    CHECK (owner_type IN ('IMOTOTO','CORPORATE','PARTNERSHIP','REFERRAL'));

-- discounts.discount_type → DiscountType
ALTER TABLE discounts DROP CONSTRAINT IF EXISTS discounts_discount_type_check;
ALTER TABLE discounts ADD CONSTRAINT discounts_discount_type_check
    CHECK (discount_type IN ('PERCENTAGE','FLAT'));

-- discounts.reset_period → ResetPeriod
ALTER TABLE discounts DROP CONSTRAINT IF EXISTS discounts_reset_period_check;
ALTER TABLE discounts ADD CONSTRAINT discounts_reset_period_check
    CHECK (reset_period IN ('NEVER','DAILY','WEEKLY','MONTHLY','YEARLY'));
