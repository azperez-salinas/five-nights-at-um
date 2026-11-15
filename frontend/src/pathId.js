// Sonar javascript:S8476 (DS-72/DS-73): ids that come from an API response or
// the URL must never reach a request path as-is, or a crafted value like
// "../users/me" would send the user's token to an unintended endpoint.
// Only positive integers written as plain digits are accepted; anything else throws.
export function pathId(id) {
    const s = String(id);
    if (!/^[1-9]\d*$/.test(s) || !Number.isSafeInteger(Number(s))) {
        throw new Error(`Invalid id: ${s}`);
    }
    return encodeURIComponent(s);
}
