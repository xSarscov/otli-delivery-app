import { DELIVERY_FEE_CENTS, NAGAROTE_LOCATION, serverTime } from "./catalog-support";

export const ORDER_ITEM = { productId: "prod-1", name: "Gallo pinto", unitPriceCents: 9000, quantity: 2 };

/**
 * A valid `orders/{id}` create payload for customer-1 ordering from the open merchant-a: two items of
 * C$ 90.00 plus the snapshotted C$ 30.00 fee. Tests override one field at a time.
 */
export function orderDoc(overrides: Record<string, unknown> = {}) {
  return {
    customerId: "customer-1",
    customerName: "Ana Lopez",
    customerPhone: "88882222",
    merchantId: "merchant-a",
    merchantName: "Comedor Nagarote",
    pickup: NAGAROTE_LOCATION,
    dropoff: { lat: 12.27, lng: -86.57, reference: "Casa azul frente a la pulperia" },
    items: [ORDER_ITEM],
    subtotalCents: 18_000,
    deliveryFeeCents: DELIVERY_FEE_CENTS,
    totalCents: 18_000 + DELIVERY_FEE_CENTS,
    paymentMethod: "cash",
    status: "placed",
    courierId: null,
    createdAt: serverTime(),
    updatedAt: serverTime(),
    ...overrides,
  };
}

/** [count] distinct line items, for the item-count bounds. */
export const itemsOf = (count: number) =>
  Array.from({ length: count }, (_, index) => ({ ...ORDER_ITEM, productId: `prod-${index + 1}` }));
