package com.motoparts.usa

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class CartLine(val product: ProductSummary, val qty: Int)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val session = Session(app)
    init { Net.init(session) }

    var brands by mutableStateOf<List<Brand>>(emptyList()); private set
    var categories by mutableStateOf<List<Category>>(emptyList()); private set
    var products by mutableStateOf<List<ProductSummary>>(emptyList()); private set
    var models by mutableStateOf<List<BikeModel>>(emptyList()); private set
    var detail by mutableStateOf<ProductDetail?>(null); private set
    var addresses by mutableStateOf<List<Address>>(emptyList()); private set
    var orders by mutableStateOf<List<OrderSummary>>(emptyList()); private set
    var order by mutableStateOf<OrderDetail?>(null); private set
    var loading by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var userName by mutableStateOf(session.userName)
    var currency by mutableStateOf(session.currency)
    val cart = mutableStateListOf<CartLine>()

    val loggedIn: Boolean get() = session.token != null

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            loading = true
            try { block() } catch (e: Exception) { message = errorMessage(e) }
            loading = false
        }
    }

    fun setCurrency(c: String) { currency = c; session.currency = c }

    fun loadHome() = run {
        brands = Net.api.brands()
        categories = Net.api.categories()
        products = Net.api.products()
    }

    fun search(q: String?, categoryId: Int? = null) = run {
        products = Net.api.products(search = q?.takeIf { it.isNotBlank() }, categoryId = categoryId)
    }

    fun loadBrand(brandId: Int) = run {
        models = emptyList(); products = emptyList()
        models = Net.api.models(brandId)
    }

    fun productsForModel(modelId: Int, year: Int?) = run {
        products = Net.api.products(modelId = modelId, year = year)
    }

    fun loadProduct(id: String) = run { detail = null; detail = Net.api.product(id) }

    fun login(email: String, pw: String, done: () -> Unit) = run {
        val r = Net.api.login(LoginReq(email.trim(), pw))
        saveAuth(r); done()
    }

    fun register(name: String, email: String, pw: String, done: () -> Unit) = run {
        val r = Net.api.register(RegisterReq(email.trim(), pw, name.trim()))
        saveAuth(r); done()
    }

    private fun saveAuth(r: AuthResp) {
        session.token = r.token; session.userName = r.user.full_name; userName = r.user.full_name
    }

    fun logout() {
        session.token = null; session.userName = null; userName = null
        orders = emptyList(); addresses = emptyList(); cart.clear()
    }

    fun addToCart(p: ProductSummary) {
        val i = cart.indexOfFirst { it.product.id == p.id }
        if (i >= 0) { if (cart[i].qty < p.stock) cart[i] = cart[i].copy(qty = cart[i].qty + 1) }
        else if (p.stock > 0) cart.add(CartLine(p, 1))
    }

    fun setQty(id: String, qty: Int) {
        val i = cart.indexOfFirst { it.product.id == id }
        if (i < 0) return
        if (qty <= 0) cart.removeAt(i) else cart[i] = cart[i].copy(qty = minOf(qty, cart[i].product.stock))
    }

    fun loadAddresses() = run { addresses = Net.api.addresses() }

    fun addAddress(a: AddressReq) = run {
        Net.api.addAddress(a)
        addresses = Net.api.addresses()
    }

    // Creates the real order, then asks the backend for a payment link. Returns the link (or null).
    fun placeOrder(addressId: String, onPayLink: (String) -> Unit) = run {
        val o = Net.api.createOrder(OrderReq(addressId, currency, cart.map { OrderItemReq(it.product.id, it.qty) }))
        cart.clear()
        try {
            val link = Net.api.pay(o.id).checkout_url
            if (link != null) onPayLink(link)
        } catch (e: Exception) {
            message = "Order placed (pending payment). " + errorMessage(e)
        }
    }

    fun payOrder(id: String, onLink: (String) -> Unit) = run { Net.api.pay(id).checkout_url?.let(onLink) }
    fun loadOrders() = run { orders = Net.api.orders() }
    fun loadOrder(id: String) = run { order = null; order = Net.api.order(id) }
    fun cancelOrder(id: String) = run { Net.api.cancel(id); order = Net.api.order(id) }
}
