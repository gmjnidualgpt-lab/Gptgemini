const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read any data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
});

test("Authenticated user: can create, read, update, delete their own session", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("session_1");

  await assertSucceeds(
    sessionRef.set({
      id: "session_1",
      userId: ALICE_UID,
      title: "Comparison Chat",
      geminiModel: "gemini-3.1-pro-preview",
      chatGptModel: "gpt-4o",
      thinkingLevel: "HIGH",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(sessionRef.get());

  await assertSucceeds(
    sessionRef.update({
      title: "Updated Title",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(sessionRef.delete());
});

test("Cross-user isolation: Bob cannot access Alice's session or messages", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("sessions").doc("session_1").set({
      id: "session_1",
      userId: ALICE_UID,
      title: "Alice Secret",
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("session_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").get());
});

test("Authenticated user: can add message and update with edited text", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const msgRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("session_1").collection("messages").doc("msg_1");

  await assertSucceeds(
    msgRef.set({
      id: "msg_1",
      userId: ALICE_UID,
      sessionId: "session_1",
      prompt: "Explain quantum computing",
      geminiResponse: "Gemini answer...",
      chatGptResponse: "ChatGPT answer...",
      geminiModel: "gemini-3.1-pro-preview",
      chatGptModel: "gpt-4o",
      geminiThinking: true,
      status: "completed",
      isEdited: false,
      editedSource: "none",
      createdAt: new Date(),
    })
  );

  // User edits Gemini's response and updates message
  await assertSucceeds(
    msgRef.update({
      isEdited: true,
      editedSource: "gemini",
      editedText: "Edited response for critique...",
      updatedAt: new Date(),
    })
  );
});
