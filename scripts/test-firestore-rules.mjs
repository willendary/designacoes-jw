import { readFileSync } from "node:fs";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";

const environment = await initializeTestEnvironment({
  projectId: "demo-designacoes-jw",
  firestore: {
    rules: readFileSync("firestore.rules", "utf8"),
    host: "127.0.0.1",
    port: 8080,
  },
});

const workspace = "workspaces/designacoes-jw";
const invitationPath = workspace + "/invitations/convite-1";
const meetingPath = workspace + "/meetings/1";
const userPath = "users/novo-usuario";
const invitation = {
  email: "convidado@example.com",
  name: "Convidado",
  permissions: ["view_assignments", "export_reports"],
  status: "pending",
  createdBy: "administrador",
  createdAt: 12345,
};

try {
  await environment.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), invitationPath), invitation);
    await setDoc(doc(ctx.firestore(), meetingPath), { date: "01/11/2026" });
    await setDoc(doc(ctx.firestore(), "users/existente"), {
      email: "existente@example.com", active: true, role: "viewer",
      permissions: ["view_assignments", "export_reports"], invitationId: "",
    });
  });

  const anonymous = environment.unauthenticatedContext();
  const stranger = environment.authenticatedContext("estranho", {
    email: "estranho@example.com", email_verified: true,
  });
  const unverified = environment.authenticatedContext("nao-verificado", {
    email: "convidado@example.com", email_verified: false,
  });
  const invited = environment.authenticatedContext("novo-usuario", {
    email: "convidado@example.com", email_verified: true,
  });

  const permitted = {
    email: "convidado@example.com",
    name: "Convidado",
    role: "custom",
    active: true,
    permissions: ["view_assignments", "export_reports"],
    invitationId: "convite-1",
  };

  await assertFails(getDoc(doc(anonymous.firestore(), meetingPath)));
  await assertFails(getDoc(doc(stranger.firestore(), meetingPath)));
  await assertFails(getDoc(doc(stranger.firestore(), invitationPath)));
  await assertFails(getDoc(doc(unverified.firestore(), invitationPath)));
  await assertFails(setDoc(doc(stranger.firestore(), "users/estranho"), {
    email: "estranho@example.com", active: true, role: "viewer",
    permissions: ["view_assignments", "export_reports"],
  }));
  await assertFails(setDoc(doc(unverified.firestore(), "users/nao-verificado"), permitted));
  await assertSucceeds(getDoc(doc(invited.firestore(), invitationPath)));
  await assertFails(setDoc(doc(invited.firestore(), userPath), {
    ...permitted, permissions: ["manage_users", "view_assignments"],
  }));
  await assertSucceeds(setDoc(doc(invited.firestore(), userPath), permitted));
  await assertSucceeds(getDoc(doc(invited.firestore(), meetingPath)));
  await assertFails(updateDoc(doc(invited.firestore(), userPath), {
    permissions: ["manage_users", "view_assignments", "export_reports"],
  }));
  await assertFails(updateDoc(doc(invited.firestore(), invitationPath), {
    email: "outro@example.com", status: "accepted", acceptedBy: "novo-usuario",
  }));
  await assertSucceeds(updateDoc(doc(invited.firestore(), invitationPath), {
    status: "accepted", acceptedBy: "novo-usuario", acceptedAt: 12346,
  }));
  await assertFails(setDoc(doc(invited.firestore(), "users/novo-usuario-2"), permitted));
  const existing = environment.authenticatedContext("existente", {
    email: "existente@example.com", email_verified: true,
  });
  await assertSucceeds(getDoc(doc(existing.firestore(), meetingPath)));
  console.log("Firestore: 15 verificações de acesso concluídas.");
} finally {
  await environment.cleanup();
}
