import CustomerPageData from 'test/customer-page-data';
import $ from 'servicedesk/jQuery';
import _ from 'servicedesk/lodash';
import CustomersPage from 'project-ui/project-app/pages/customers/customers-page-view';
import PaginationModel from 'project-ui/project-app/pages/customers/pagination/model/customer-pagination-page-model';

describe('Customers page', () => {
    let customersPage;

    const createRenderedCustomerPage = options => {
        let pageModelData;

        if (options && options.noCustomers) {
            pageModelData = CustomerPageData.noCustomers();
        } else {
            pageModelData = _.merge(CustomerPageData.paginationPageData(), options);
        }

        const pageModel = new PaginationModel(pageModelData);
        const $el = $('<div />');

        customersPage = new CustomersPage({
            model: pageModel,
            el: $el,
        });

        customersPage.render();
    };

    const getMessage = () => customersPage.$('.sd-customer-manage').text();
    const getMessageLink = () => customersPage.$('.sd-customer-manage a').attr('href');
    const getAddOrganizationButton = () => customersPage.$('.js-add-organizations');
    const getInviteCustomerButton = () => customersPage.$('.js-invite-customers');
    const getBlankPageImage = () => customersPage.$('.sd-user-and-org-blank-page.sd-customer-blank-page');
    const getBlankPageMessages = () => customersPage.$('.sd-user-and-org-blank-page-messages');

    describe('when user can administer', () => {
        beforeEach(() => {
            createRenderedCustomerPage({
                canAdminister: true,
                serviceDeskOpenAccess: false,
            });
        });

        it('should have a message', () => {
            const expectedMsg = 'sd.agent.people.customers.list.access.restricted.message,,';
            const actualMsg = getMessage();
            expect(actualMsg).toEqual(expectedMsg);
        });

        it('should have a link inside a message', () => {
            expect(getMessageLink()).toEqual('/jira/servicedesk/admin/FAKEPROJETKEY/request-security');
        });
    });

    describe('when user can administer and have open service desk', () => {
        beforeEach(() => {
            createRenderedCustomerPage({
                canAdminister: true,
                serviceDeskPublicSignUp: true,
            });
        });

        it('should have a message', () => {
            const expectedMsg = 'sd.agent.people.customers.list.access.open.message,,';
            const actualMsg = getMessage();
            expect(actualMsg).toEqual(expectedMsg);
        });

        it('should have a link inside a message', () => {
            expect(getMessageLink()).toEqual('/jira/servicedesk/admin/FAKEPROJETKEY/request-security');
        });
    });

    describe("when user can administer and have 'any user' service desk", () => {
        beforeEach(() => {
            createRenderedCustomerPage({
                canManageOrganizations: true,
                serviceDeskOpenAccess: true,
            });
        });

        it('should have a message', () => {
            const expectedMsg = 'sd.agent.people.customers.list.access.account.message,,';
            expect(getMessage()).toEqual(expectedMsg);
        });

        it('should have a link inside a message', () => {
            expect(getMessageLink()).toEqual('/jira/servicedesk/admin/FAKEPROJETKEY/request-security');
        });
    });

    describe('when user can not add organizations', () => {
        beforeEach(() => {
            createRenderedCustomerPage({
                canManageOrganizations: false,
            });
        });

        it("should have no 'Add organizations' button rendered", () => {
            expect(getAddOrganizationButton().length).toBe(0);
        });
    });

    describe('when user can not invite customers', () => {
        beforeEach(() => {
            createRenderedCustomerPage({
                canInvite: false,
            });
        });

        it("should have no 'Add customers' button rendered", () => {
            expect(getInviteCustomerButton().length).toBe(0);
        });
    });

    describe('when user can add organizations', () => {
        beforeEach(() => {
            createRenderedCustomerPage({
                canManageOrganizations: true,
            });
        });

        it("should have 'Add organizations' button rendered", () => {
            expect(getAddOrganizationButton().length).toBe(1);
        });
    });

    describe('when user can invite customers', () => {
        beforeEach(() => {
            createRenderedCustomerPage({
                canInvite: true,
            });
        });

        it("should have 'Add customers' button rendered", () => {
            expect(getInviteCustomerButton().length).toBe(1);
        });
    });

    describe('when user can not administer', () => {
        beforeEach(() => {
            createRenderedCustomerPage({
                canAdminister: false,
            });
        });

        it('should have an empty message', () => {
            expect(getMessage()).toBe('');
        });
    });

    describe('when no customers', () => {
        beforeEach(() => {
            createRenderedCustomerPage({
                noCustomers: true,
            });
        });

        it('should render a blank page image', () => {
            expect(getBlankPageImage().length).toBe(1);
        });

        it('should render a blank page messages', () => {
            expect(getBlankPageMessages().length).toBe(1);
        });
    });

    describe('when inviting customers', () => {
        beforeEach(() => {
            createRenderedCustomerPage({
                canManageOrganizations: true,
                canInvite: true,
            });

            jest.spyOn(customersPage.userAndOrgListingView, 'refreshListing');
        });

        it('should refresh customer and org listing on success', () => {
            customersPage.actionsView.trigger('inviteSuccess');
            expect(customersPage.userAndOrgListingView.refreshListing).toBeCalledTimes(1);
        });

        it('should refresh customer and org listing on failure', () => {
            customersPage.actionsView.trigger('inviteFailed');
            expect(customersPage.userAndOrgListingView.refreshListing).toBeCalledTimes(1);
        });
    });
});

describe('', () => {});
