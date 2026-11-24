package in.hisaab.categories;

import static in.hisaab.TestFixtures.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class CategoriesApiTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  @Test
  void newPersonalWorkspaceHasTheDefaultCategories() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    // 10 expense + 5 income categories, no subcategories.
    String body =
        mockMvc
            .perform(get(url(ws)).cookie(auth(s)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(15))
            .andExpect(jsonPath("$.items[0].kind").value("expense"))
            .andExpect(jsonPath("$.items[?(@.is_system == false)]").isEmpty())
            .andExpect(jsonPath("$.items[?(@.name == 'Money Lent')]").isNotEmpty())
            .andExpect(jsonPath("$.items[?(@.name == 'Loan Repayment')]").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // JsonPath.read directly: MockMvc's jsonPath(...).value(List) does not match filter results.
    List<String> income = JsonPath.read(body, "$.items[?(@.kind == 'income')].name");
    assertThat(income)
        .containsExactly(
            "Salary", "Refunds", "Money Borrowed", "Repayment Received", "Other Income");
  }

  @Test
  void defaultsHaveNoSubcategories() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    String body =
        mockMvc
            .perform(get(url(ws)).cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> children = JsonPath.read(body, "$.items[?(@.parent_id != null)].name");
    assertThat(children).isEmpty();
  }

  @Test
  void createAChildCategory() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String food = idOf(s, ws, "Food", "expense");

    postCat(
            s,
            ws,
            "{\"name\": \" Coffee \", \"kind\": \"expense\", \"parent_id\": \"%s\"}"
                .formatted(food))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Coffee"))
        .andExpect(jsonPath("$.parent_id").value(food))
        .andExpect(jsonPath("$.is_system").value(false))
        .andExpect(jsonPath("$.hidden_at").doesNotExist())
        // Food has no other children, so the new one is first.
        .andExpect(jsonPath("$.sort_order").value(0));
  }

  @Test
  void aChildOfAChildIsRejected() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String groceries = createCat(s, ws, "Groceries", "expense", idOf(s, ws, "Food", "expense"));

    postCat(
            s,
            ws,
            "{\"name\": \"Veg\", \"kind\": \"expense\", \"parent_id\": \"%s\"}"
                .formatted(groceries))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_parent"));
  }

  @Test
  void aParentOfTheOtherKindIsRejected() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String food = idOf(s, ws, "Food", "expense");

    postCat(
            s,
            ws,
            "{\"name\": \"Tips\", \"kind\": \"income\", \"parent_id\": \"%s\"}".formatted(food))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_parent"));
  }

  @Test
  void aParentFromAnotherWorkspaceIsRejected() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();
    String bsFood = idOf(b, fixtures.workspaceIdOf(b), "Food", "expense");

    postCat(
            a,
            fixtures.workspaceIdOf(a),
            "{\"name\": \"Coffee\", \"kind\": \"expense\", \"parent_id\": \"%s\"}"
                .formatted(bsFood))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_parent"));
  }

  @Test
  void renameAndHideADefaultCategory() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String shopping = idOf(s, ws, "Shopping", "expense");

    patchCat(s, ws, shopping, "{\"name\": \"Retail\", \"hidden\": true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Retail"))
        .andExpect(jsonPath("$.hidden_at").exists())
        .andExpect(jsonPath("$.is_system").value(true));
    // Hidden categories still appear in the list; the client decides how to show them.
    patchCat(s, ws, shopping, "{\"hidden\": false}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Retail"))
        .andExpect(jsonPath("$.hidden_at").doesNotExist());
  }

  @Test
  void aDefaultCategoryCannotBeDeleted() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    mockMvc
        .perform(delete(url(ws) + "/" + idOf(s, ws, "Money Lent", "expense")).cookie(auth(s)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("system_category"));
  }

  @Test
  void aUserCategoryCanBeDeletedUnlessItHasChildren() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String hobby = createCat(s, ws, "Hobby", "expense", null);
    String chess = createCat(s, ws, "Chess", "expense", hobby);

    mockMvc
        .perform(delete(url(ws) + "/" + hobby).cookie(auth(s)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("category_in_use"));

    mockMvc
        .perform(delete(url(ws) + "/" + chess).cookie(auth(s)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(delete(url(ws) + "/" + hobby).cookie(auth(s)))
        .andExpect(status().isNoContent());
    assertThat(findIds(s, ws, "Hobby", "expense")).isEmpty();
  }

  @Test
  void duplicateSiblingNamesIgnoringCaseGet409() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String food = idOf(s, ws, "Food", "expense");
    createCat(s, ws, "Groceries", "expense", food);

    // Top level: "Food" already exists.
    postCat(s, ws, "{\"name\": \"FOOD\", \"kind\": \"expense\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("name_taken"));
    // Same parent: "Groceries" already exists under Food.
    postCat(
            s,
            ws,
            "{\"name\": \"groceries\", \"kind\": \"expense\", \"parent_id\": \"%s\"}"
                .formatted(food))
        .andExpect(status().isConflict());
    // Renaming onto an existing sibling.
    patchCat(s, ws, idOf(s, ws, "Health", "expense"), "{\"name\": \"shopping\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("name_taken"));
  }

  @Test
  void theSameNameIsAllowedUnderADifferentParentOrKind() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String food = idOf(s, ws, "Food", "expense");

    // "Other" exists at the top level of expense; as a child of Food it is a different sibling set.
    postCat(
            s,
            ws,
            "{\"name\": \"Other\", \"kind\": \"expense\", \"parent_id\": \"%s\"}".formatted(food))
        .andExpect(status().isCreated());
    // Income is a separate set from expense.
    postCat(s, ws, "{\"name\": \"Other\", \"kind\": \"income\"}").andExpect(status().isCreated());
  }

  @Test
  void invalidInputGets400() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    postCat(s, ws, "{\"name\": \"  \", \"kind\": \"expense\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields.name").exists());
    postCat(s, ws, "{\"name\": \"X\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields.kind").exists());
    postCat(s, ws, "{\"name\": \"X\", \"kind\": \"transfer\"}").andExpect(status().isBadRequest());
    patchCat(s, ws, idOf(s, ws, "Health", "expense"), "{\"name\": \"  \"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void nonMemberGets404OnEveryEndpoint() throws Exception {
    Session owner = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(owner);
    String ownersCategory = createCat(owner, ws, "Private", "expense", null);
    Session intruder = fixtures.signup();

    mockMvc.perform(get(url(ws)).cookie(auth(intruder))).andExpect(status().isNotFound());
    postCat(intruder, ws, "{\"name\": \"Sneaky\", \"kind\": \"expense\"}")
        .andExpect(status().isNotFound());
    patchCat(intruder, ws, ownersCategory, "{\"name\": \"Hacked\"}")
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(url(ws) + "/" + ownersCategory).cookie(auth(intruder)))
        .andExpect(status().isNotFound());

    // The owner's data is untouched.
    assertThat(findIds(owner, ws, "Private", "expense")).hasSize(1);
    assertThat(findIds(owner, ws, "Sneaky", "expense")).isEmpty();
    assertThat(findIds(owner, ws, "Hacked", "expense")).isEmpty();
  }

  @Test
  void categoryIdFromAnotherWorkspaceGets404() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();
    String bsCategory = createCat(b, fixtures.workspaceIdOf(b), "Mine", "expense", null);
    UUID aws = fixtures.workspaceIdOf(a);

    patchCat(a, aws, bsCategory, "{\"name\": \"Stolen\"}").andExpect(status().isNotFound());
    mockMvc
        .perform(delete(url(aws) + "/" + bsCategory).cookie(auth(a)))
        .andExpect(status().isNotFound());
  }

  private static String url(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/categories";
  }

  private ResultActions postCat(Session s, UUID ws, String json) throws Exception {
    return mockMvc.perform(
        post(url(ws)).cookie(auth(s)).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions patchCat(Session s, UUID ws, String id, String json) throws Exception {
    return mockMvc.perform(
        patch(url(ws) + "/" + id)
            .cookie(auth(s))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private String createCat(Session s, UUID ws, String name, String kind, String parentId)
      throws Exception {
    String parent = parentId == null ? "" : ", \"parent_id\": \"%s\"".formatted(parentId);
    String body =
        postCat(s, ws, "{\"name\": \"%s\", \"kind\": \"%s\"%s}".formatted(name, kind, parent))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private List<String> findIds(Session s, UUID ws, String name, String kind) throws Exception {
    String body =
        mockMvc
            .perform(get(url(ws)).cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(
        body, "$.items[?(@.name == '%s' && @.kind == '%s')].id".formatted(name, kind));
  }

  private String idOf(Session s, UUID ws, String name, String kind) throws Exception {
    List<String> ids = findIds(s, ws, name, kind);
    assertThat(ids).as("category %s (%s)", name, kind).hasSize(1);
    return ids.get(0);
  }
}
