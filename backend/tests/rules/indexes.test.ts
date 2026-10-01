import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

interface IndexField {
  fieldPath: string;
  order: "ASCENDING" | "DESCENDING";
}
interface IndexDefinition {
  collectionGroup: string;
  queryScope: string;
  fields: IndexField[];
}

const { indexes } = JSON.parse(readFileSync("firestore.indexes.json", "utf8")) as { indexes: IndexDefinition[] };

/** The fields of the index on [collection], rendered as "field:ORDER" pairs, or undefined when absent. */
const indexOn = (collection: string, ...fields: string[]) =>
  indexes.find(
    (index) =>
      index.collectionGroup === collection &&
      index.fields.map((field) => `${field.fieldPath}:${field.order}`).join(",") === fields.join(","),
  );

describe("composite indexes the queries of the design depend on", () => {
  it("has the order history index for customers, newest first", () => {
    expect(indexOn("orders", "customerId:ASCENDING", "createdAt:DESCENDING")).toBeDefined();
  });

  it("has the incoming and active orders index for merchants, newest first", () => {
    expect(indexOn("orders", "merchantId:ASCENDING", "createdAt:DESCENDING")).toBeDefined();
  });

  it("has the courier pool index: ready orders, oldest first", () => {
    expect(indexOn("orders", "status:ASCENDING", "readyAt:ASCENDING")).toBeDefined();
  });

  it("keeps the earlier indexes", () => {
    expect(indexOn("users", "status:ASCENDING", "createdAt:ASCENDING")).toBeDefined();
    expect(indexOn("merchants", "status:ASCENDING", "name:ASCENDING")).toBeDefined();
  });

  it("does not define the same index twice", () => {
    const keys = indexes.map((index) => `${index.collectionGroup}|${index.fields.map((f) => `${f.fieldPath}:${f.order}`).join(",")}`);
    expect(new Set(keys).size).toBe(keys.length);
  });
});
